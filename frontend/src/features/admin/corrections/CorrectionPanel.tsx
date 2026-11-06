import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { ApiError, errorMessage } from '../../../api';
import type { LeaveGuard } from '../AdminWorkspace';
import { registrationApi } from '../registration/registrationApi';
import { restaurantApi } from '../restaurants/restaurantApi';
import type { Chef, Video } from '../registration/types';
import type { Page, RestaurantItem } from '../restaurants/types';
import { correctionApi } from './correctionApi';
import type { AuditValue, CorrectionTarget, HistoryEvent, Revision, RevisionList, Snapshot } from './types';

const labels: Record<string, string> = { chefId:'셰프 ID', restaurantId:'식당 ID', videoId:'현재 영상 ID', mentionType:'관계 유형', timestampSeconds:'시점(초)', verificationNote:'검증 메모', name:'이름', roadAddress:'도로명 주소', address:'지번 주소', longitude:'경도', latitude:'위도', placeUrl:'장소 링크', evidence:'보존한 원본 근거', candidateId:'원본 후보 ID', candidateEvidence:'원본 후보 근거', candidateVideoId:'원본 후보 영상 ID', candidateVideoTitle:'원본 후보 영상', version:'버전', chefName:'셰프', restaurantName:'식당', videoTitle:'현재 영상', verificationStatus:'검증 상태', publicationStatus:'공개 상태' };
function display(value: unknown): string { return value === null || value === undefined ? '미확인' : String(value); }
function SnapshotView({ value }: { value: Snapshot | null }) {
  if (!value) return <p>기준 스냅샷 없음 · 명시적 기준 갱신이 필요합니다.</p>;
  return <dl>{Object.keys(labels).filter(key => key in value).map(key => <div key={key}><dt>{labels[key]}</dt><dd>{display(value[key])}</dd></div>)}</dl>;
}
function AuditSnapshot({ value }: { value: AuditValue | null }) {
  if(!value)return <SnapshotView value={null}/>;
  const nested=(key:string)=>typeof value[key]==='object'&&value[key]!==null?value[key] as Snapshot:null;
  const confirmation=value.approvalConfirmation;
  return <>{confirmation&&<section aria-label="승인 당시 영향 확인"><h4>승인 당시 영향 확인</h4><dl>
    <dt>승인 요청 hash</dt><dd>{confirmation.expectedImpactHash}</dd><dt>적용 전 서버 검증 hash</dt><dd>{confirmation.validatedImpactHash}</dd>
    <dt>승인 당시 대상 버전</dt><dd>{confirmation.targetVersion}</dd><dt>승인 당시 정정안 버전</dt><dd>{confirmation.revisionVersion}</dd>
    <dt>전체 영향 관계</dt><dd>{confirmation.impactTotalElements}개</dd><dt>공개 영향 관계</dt><dd>{confirmation.publicImpactCount}개</dd>
  </dl></section>}<SnapshotView value={nested('target')??nested('currentSnapshot')??nested('mention')??nested('draft')??value}/>{nested('baseSnapshot')&&<><p>처리 당시 기준본</p><SnapshotView value={nested('baseSnapshot')}/></>}{nested('proposedValues')&&<><p>처리 당시 제안값</p><SnapshotView value={nested('proposedValues')}/></>}{nested('revision')&&<details><summary>처리 당시 정정안</summary><AuditSnapshot value={nested('revision')}/></details>}</>;
}
interface Pending { id: number | null; action: 'create' | 'patch' | 'approve' | 'rebase'; body: Snapshot; key?: string }
export default function CorrectionPanel({ target, username, authGeneration, onExpired, onGuard, onBack, onApproved }: {
  target: CorrectionTarget; username: string | undefined; authGeneration: number; onExpired: () => void;
  onGuard: (guard: LeaveGuard | null) => void; onBack: () => void; onApproved: (result: Revision) => Promise<void>;
}) {
  const [list, setList] = useState<RevisionList>(); const [history, setHistory] = useState<Page<HistoryEvent>>();
  const [revision, setRevision] = useState<Revision>(); const [values, setValues] = useState<Snapshot>({});
  const [reason, setReason] = useState(''); const [baseline, setBaseline] = useState('');
  const [listPage, setListPage] = useState(0); const [historyPage, setHistoryPage] = useState(0); const [refresh, setRefresh] = useState(0);
  const [error, setError] = useState(''); const [readError, setReadError] = useState(''); const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState(false); const [pending, setPending] = useState<Pending>();
  const [leaving, setLeaving] = useState<(() => void) | null>(null);
  const [chefs, setChefs] = useState<Chef[]>([]); const [videos, setVideos] = useState<Video[]>([]);
  const [query, setQuery] = useState(''); const [restaurants, setRestaurants] = useState<RestaurantItem[]>([]);
  const [places, setPlaces] = useState<Awaited<ReturnType<typeof registrationApi.places>>>();
  const [token, setToken] = useState(''); const [readEpoch, setReadEpoch] = useState(-1);
  const epoch = useRef(0), selection = useRef(0), lock = useRef(false), search = useRef(0);
  const auth = useRef(username); auth.current = username;
  const readonly = revision?.status === 'APPLIED';
  const eligible = target.kind!=='MENTION'||list?.target.verificationStatus==='APPROVED';
  const dirty = !readonly && JSON.stringify({ values, reason, token }) !== baseline && (!!reason || Object.keys(values).length > 0 || !!token);
  const disabled = busy || !!pending || !username;
  function failure(e: unknown) { if (e instanceof ApiError && e.status === 401) onExpired(); }
  function writeError(e:unknown) {
    const messages:Record<string,string>={STALE_VERSION:'다른 관리자가 정정안을 수정했습니다. 최신본을 조회해 입력과 비교하세요.',STALE_BASE_VERSION:'원본 또는 참조 정보가 변경됐습니다. 최신본을 비교하고 명시적으로 기준을 갱신하세요.',IMPACT_CHANGED:'연결 관계 영향이 변경됐습니다. 최신 정정안을 조회해 영향을 다시 확인하세요.',DUPLICATE_RELATION:'같은 셰프·식당·영상의 관계가 이미 있습니다. 자동 병합·복구하지 않습니다.',OPEN_REVISION_EXISTS:'미완료 정정안이 이미 있습니다. 목록에서 이어쓰세요.',REVISION_ALREADY_APPLIED:'이미 적용된 정정안입니다. 최신 상태를 조회하세요.',IDEMPOTENCY_CONFLICT:'다른 요청에 사용된 키입니다. 새 키로 우회하지 말고 서버 상태를 확인하세요.',NO_CHANGES:'적용할 실제 변경이 없습니다.',PLACE_ID_MISMATCH:'같은 Kakao Place ID의 공식 장소를 선택하세요.',PLACE_SELECTION_EXPIRED:'장소 선택이 만료됐습니다. 입력은 유지됩니다. 공식 장소를 다시 선택하세요.'};
    return e instanceof ApiError?messages[e.code??'']??errorMessage(e):errorMessage(e);
  }
  function guarded(next: () => void) {
    if (lock.current || pending) { setError('처리 중이거나 결과가 불명확한 요청을 먼저 확인하세요.'); return; }
    if (dirty) setLeaving(() => next); else next();
  }
  useEffect(() => { onGuard(guarded); return () => onGuard(null); });
  useEffect(() => {
    if (!dirty && !pending && !busy) return;
    const before = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ''; };
    window.addEventListener('beforeunload', before); return () => window.removeEventListener('beforeunload', before);
  }, [dirty, pending, busy]);
  useEffect(() => {
    const gen = ++epoch.current, controller = new AbortController(); selection.current++; search.current++;
    setList(undefined); setHistory(undefined); setReadEpoch(authGeneration); setReadError(''); setBusy(false);
    if (!username) { setChefs([]); setVideos([]); setRestaurants([]); setPlaces(undefined); setToken(''); return () => controller.abort(); }
    void Promise.allSettled([correctionApi.list(target, listPage, controller.signal), correctionApi.history(target, historyPage, controller.signal)]).then(results => {
      if (controller.signal.aborted || gen !== epoch.current) return;
      if (results[0].status === 'fulfilled') {
        setList(results[0].value);
        if (!revision && !Object.keys(values).length && !reason) {
          const next=results[0].value.target;setValues(next);setBaseline(JSON.stringify({values:next,reason:'',token:''}));
        }
      } else { failure(results[0].reason); setReadError('정정안 목록 조회에 실패했습니다.'); }
      if (results[1].status === 'fulfilled') setHistory(results[1].value); else { failure(results[1].reason); setReadError(e => e + ' 이력 조회에 실패했습니다.'); }
    });
    if (target.kind === 'MENTION') void Promise.all([registrationApi.chefs(), registrationApi.videos()]).then(([c,v]) => {
      if (gen === epoch.current && !controller.signal.aborted) { setChefs(c); setVideos(v); }
    }).catch(e => { if (gen === epoch.current && !controller.signal.aborted) { failure(e); setReadError('기존 참조 목록 조회에 실패했습니다.'); } });
    return () => { controller.abort(); epoch.current++; selection.current++; search.current++; };
  }, [target.kind, target.id, username, authGeneration, listPage, historyPage, refresh]);
  // Fetching list/history must not erase a successfully saved editor or its input.
  async function load(id: number, impactPage = 0, replaceInput = true) {
    const request=++selection.current, gen=epoch.current;
    try {
      const data=await correctionApi.get(target,id,impactPage);
      if (request!==selection.current || gen!==epoch.current || !auth.current) return;
      setRevision(data); setReadError('');
      if (replaceInput) accept(data);
      else setBaseline(JSON.stringify({values:data.proposedValues,reason:data.changeReason,token:''}));
    } catch(e) { if (request===selection.current && gen===epoch.current && auth.current) { failure(e); setReadError(errorMessage(e)); } }
  }
  function accept(data: Revision) {
    search.current++;setPlaces(undefined);setRestaurants([]);
    setRevision(data);setValues(data.proposedValues);setReason(data.changeReason);setToken('');
    setBaseline(JSON.stringify({values:data.proposedValues,reason:data.changeReason,token:''}));
  }
  function newRevision() {
    selection.current++;search.current++;setPlaces(undefined);setRestaurants([]);
    setRevision(undefined);const next=list?.target ?? {};setValues(next);setReason('');setToken('');setError('');setNotice('');
    setBaseline(JSON.stringify({values:next,reason:'',token:''}));
  }
  async function searchChoices() {
    const gen=epoch.current,request=++search.current;
    try {
      if (target.kind==='RESTAURANT') {
        const result=await registrationApi.places(query);
        if(gen===epoch.current&&request===search.current&&auth.current)setPlaces(result);
      } else {
        const result=await restaurantApi.list({query:query.trim(),publication:'ALL',needsSourceReview:false,page:0,size:20});
        if(gen===epoch.current&&request===search.current&&auth.current)setRestaurants(result.items);
      }
    } catch(e) {if(gen===epoch.current&&request===search.current&&auth.current){failure(e);setError(errorMessage(e));}}
  }
  function body(): Snapshot {
    const expectedVersion=revision?.version ?? Number(list?.target.version);
    const result: Snapshot={expectedVersion,changeReason:reason};
    if(target.kind==='CHEF')result.proposedName=values.name;
    else if(target.kind==='RESTAURANT') { if(token)result.placeSelectionToken=token; }
    else {
      const changes: Snapshot={};for(const field of ['chefId','restaurantId','videoId','mentionType','timestampSeconds','verificationNote']) {
        if (field in values) changes[field]=values[field];
      }
      result.changes=changes;
    }
    return result;
  }
  async function write(operation: Pending) {
    if(lock.current||!auth.current)return;lock.current=true;selection.current++;search.current++;setBusy(true);setError('');
    const gen=epoch.current;
    try {
      const result=await correctionApi.write(target,operation.id,operation.action,operation.body,operation.key);
      if(gen!==epoch.current||!auth.current)return;
      setPending(undefined);
      if(operation.action==='rebase'&&dirty) {setRevision(result);setBaseline(JSON.stringify({values:result.proposedValues,reason:result.changeReason,token:''}));}
      else accept(result);
      setNotice(operation.action==='approve'?`재승인 완료 · 정정안 #${result.id} · 대상 #${result.targetId}`:'정정안을 저장했습니다. 공개본은 변경되지 않았습니다.');
      // Reads are isolated from write classification and never trigger another write.
      try {
        const [nextList,nextHistory]=await Promise.all([correctionApi.list(target,listPage),correctionApi.history(target,historyPage)]);
        if(gen===epoch.current&&auth.current){setList(nextList);setHistory(nextHistory);setReadError('');}
      } catch(e) {if(gen===epoch.current&&auth.current){failure(e);setReadError('저장·재승인은 완료됐지만 후속 조회에 실패했습니다. 조회만 다시 시도하세요.');}}
      if(operation.action==='approve'&&gen===epoch.current&&auth.current)try {await onApproved(result);}catch {if(gen===epoch.current&&auth.current)setReadError('재승인은 완료됐지만 관련 조회 갱신에 실패했습니다. 조회만 다시 시도하세요.');}
    } catch(e) {
      if(gen!==epoch.current||!auth.current)return;
      failure(e);setError(writeError(e));
      if(e instanceof ApiError&&e.code==='PLACE_SELECTION_EXPIRED'){setToken('');setPlaces(undefined);}
      if(e instanceof ApiError&&e.status<500&&e.code!=='IDEMPOTENCY_CONFLICT')setPending(undefined);
      if(!(e instanceof ApiError)||e.status>=500) {
        setPending(operation);setError('결과가 불명확합니다. 같은 요청으로 결과를 확인하세요. PATCH/rebase는 최신본 조회 후 비교하며 자동 재전송하지 않습니다.');
      }
    } finally {lock.current=false;if(gen===epoch.current)setBusy(false);}
  }
  function submit() {
    if(!reason.trim()||reason.length>2000){setError('정정 사유는 필수이며 2,000자 이하여야 합니다.');return;}
    if(target.kind==='RESTAURANT'&&!revision&&!token){setError('같은 Kakao 장소의 공식 검색 결과를 선택하세요.');return;}
    if(target.kind==='RESTAURANT'&&revision&&!token&&['name','roadAddress','address','longitude','latitude','placeUrl'].some(f=>values[f]!==revision.proposedValues[f])){
      setError('장소 선택을 다시 확인해야 합니다. 공식 검색 결과를 다시 선택하세요.');return;
    }
    void write({id:revision?.id??null,action:revision?'patch':'create',body:body(),key:revision?undefined:crypto.randomUUID()});
  }
  const visible=!!username&&readEpoch===authGeneration;
  return <section aria-label="관리자 정보 정정"><button onClick={()=>guarded(onBack)}>← 식당 상세로</button><h2>정보 정정 · 변경 이력</h2>
    {!username&&<p>로그인이 필요합니다. 같은 계정으로 재로그인하면 입력을 이어갈 수 있습니다.</p>}
    {visible&&<>
      <p>대상 #{target.id} · {target.kind==='MENTION'?'관계':target.kind==='CHEF'?'셰프 이름 표기':'공식 장소 정보'}. 정정안 저장은 공개본을 바꾸지 않습니다.</p>
      <button disabled={busy} onClick={()=>setRefresh(n=>n+1)}>정정안·이력 새로고침</button>
      {notice&&<p role="status">{notice}</p>}{error&&<p role="alert">{error}</p>}
      {readError&&<p role="alert">{readError} <button disabled={busy} onClick={()=>{setRefresh(n=>n+1);}}>정정·이력 조회 다시 시도</button></p>}
      {!list&&!readError&&<p role="status">정정안 목록을 불러오는 중…</p>}
      {list&&<><h3>정정안</h3>{list.items.map(r=><button disabled={disabled} key={r.id} onClick={()=>guarded(()=>void load(r.id))}>정정안 #{r.id} · {r.status} · {r.changeReason}</button>)}
        <button disabled={disabled||!eligible||list.items.some(r=>r.status==='DRAFT')} onClick={()=>guarded(newRevision)}>새 정정안 작성</button>
        <button disabled={disabled||listPage===0} onClick={()=>setListPage(n=>n-1)}>이전 정정안 페이지</button><button disabled={disabled||!list.hasNext} onClick={()=>setListPage(n=>n+1)}>다음 정정안 페이지</button></>}
      {revision&&<><h3>기준본 · 현재본 · 제안값 비교</h3>
        {revision.baseConflict&&<p role="alert">원본 기준 버전 충돌 · 최신본을 비교하고 명시적으로 기준을 갱신하세요.</p>}
        {revision.referenceConflict&&<p role="alert">참조 정보가 변경됐습니다. 다시 검토하고 기준을 갱신하세요.</p>}
        <table><thead><tr><th>항목</th><th>기준본</th><th>현재본</th><th>제안값</th></tr></thead><tbody>{Object.keys(revision.proposedValues).map(f=><tr key={f}><th>{labels[f]??f}{revision.changedFields.includes(f)?' · 변경':''}</th><td>{display(revision.baseSnapshot?.[f])}</td><td>{display(revision.currentSnapshot[f])}</td><td>{display(values[f])}</td></tr>)}</tbody></table>
        <p>원본 버전 {revision.baseVersion} → 현재 {display(revision.currentSnapshot.version)} · 정정안 버전 {revision.version}</p>
        <p>연결 관계 {revision.impact.totalElements}개 · 공개 영향 {revision.publicImpactCount}개</p>
        {revision.impact.items.map(m=><p key={String(m.id)}>관계 #{display(m.id)} · {display(m.chefName)} · {display(m.restaurantName)} · {display(m.videoTitle)} · {display(m.publicationStatus)}</p>)}
        <button disabled={disabled||revision.impact.page===0} onClick={()=>void load(revision.id,revision.impact.page-1,false)}>이전 영향 페이지</button><button disabled={disabled||!revision.impact.hasNext} onClick={()=>void load(revision.id,revision.impact.page+1,false)}>다음 영향 페이지</button>
        <details><summary>보존할 원본 근거</summary><SnapshotView value={revision.currentSnapshot}/></details>
        <button disabled={busy} onClick={()=>void load(revision.id,0,false)}>최신 정정안 조회 · 입력 유지</button>
      </>}
      {list&&!eligible&&<p>승인된 관계만 정정할 수 있습니다. 이력은 조회할 수 있습니다.</p>}
      {list&&!readonly&&<fieldset disabled={disabled||!eligible}><legend>정정 제안 · 기존 자료만 선택</legend>
        {target.kind==='RESTAURANT'&&<p>기준 갱신은 기준본만 갱신하며 저장된 공식 장소 제안 전체를 유지합니다. 새 공식 정보는 장소를 다시 선택해 저장하세요. 이전 방식으로 혼합된 제안은 자동 복구되지 않으므로 공식 결과를 다시 선택하세요.</p>}
        {target.kind==='MENTION'&&<>
          <label>정정 셰프<select value={String(values.chefId??'')} onChange={e=>setValues(v=>({...v,chefId:Number(e.target.value)}))}><option value="">선택</option>{chefs.map(c=><option key={c.id} value={c.id}>{c.name} · ID {c.id}</option>)}</select></label>
          <label>정정 영상<select value={String(values.videoId??'')} onChange={e=>setValues(v=>({...v,videoId:e.target.value}))}><option value="">선택</option>{videos.map(v=><option key={v.id} value={v.id}>{v.title} · {v.id}</option>)}</select></label>
          <label>정정 유형<select value={String(values.mentionType??'')} onChange={e=>setValues(v=>({...v,mentionType:e.target.value}))}><option value="">선택</option>{['REGULAR','RECOMMENDED','VISITED','MENTIONED'].map(v=><option key={v}>{v}</option>)}</select></label>
          <p>선택 식당 ID {display(values.restaurantId)}</p>
          <label>확인된 시점(초) · 공란은 미확인<input type="number" min="0" max="2147483647" value={values.timestampSeconds===null?'':String(values.timestampSeconds??'')} onChange={e=>setValues(v=>({...v,timestampSeconds:e.target.value===''?null:Number(e.target.value)}))}/></label>
          <label>정정 검증 메모<textarea maxLength={2000} value={String(values.verificationNote??'')} onChange={e=>setValues(v=>({...v,verificationNote:e.target.value||null}))}/></label>
        </>}
        {target.kind==='CHEF'&&<label>같은 사람의 이름 표기<input maxLength={100} value={String(values.name??'')} onChange={e=>setValues(v=>({...v,name:e.target.value}))}/></label>}
        {target.kind!=='CHEF'&&<><label>{target.kind==='RESTAURANT'?'같은 Kakao 장소 검색':'기존 식당 검색'}<input value={query} onChange={e=>{search.current++;setQuery(e.target.value);setPlaces(undefined);setRestaurants([]);}}/></label><button onClick={()=>void searchChoices()}>정정 장소 찾기</button>
          {restaurants.map(r=><button key={r.id} onClick={()=>setValues(v=>({...v,restaurantId:r.id}))}>{r.name} · {r.roadAddress??r.address} · ID {r.id}</button>)}
          {places&&<><p>공식 검색 결과 {places.totalCount}개{places.complete?'':' · 일부 결과'}</p>{places.candidates.map(p=><button key={p.placeSelectionToken} onClick={()=>{setToken(p.placeSelectionToken);setValues({...p.place});}}>{p.place.name} · {p.place.roadAddress??p.place.address} · Kakao ID {p.place.id}</button>)}</>}
          {token&&<p>공식 장소 스냅샷 선택됨 · {display(values.name)} · {display(values.roadAddress)} · {display(values.address)}</p>}
        </>}
        <label>필수 정정 사유<textarea maxLength={2000} value={reason} onChange={e=>setReason(e.target.value)}/></label><button onClick={submit}>정정안 저장</button>
        {revision&&<><button onClick={()=>void write({id:revision.id,action:'rebase',body:{expectedVersion:revision.version,expectedBaseVersion:revision.currentSnapshot.version}})}>비교한 현재본으로 기준 갱신</button>
          <button disabled={dirty||revision.baseConflict||revision.referenceConflict} onClick={()=>void write({id:revision.id,action:'approve',key:crypto.randomUUID(),body:{expectedVersion:revision.version,expectedImpactHash:revision.impactHash}})}>영향 확인 · 재승인</button></>}
      </fieldset>}
      {readonly&&<p>APPLIED · 적용 완료. 정정안은 읽기 전용이며 새 정정안을 작성할 수 있습니다.</p>}
      {pending&&<section><h3>요청 결과 확인</h3>{pending.action==='create'||pending.action==='approve'?<button disabled={busy} onClick={()=>void write(pending)}>동일 키·동일 요청으로 결과 재확인</button>:<><p>PATCH/rebase는 자동 재전송하지 않습니다.</p><button disabled={busy} onClick={()=>{if(revision)void load(revision.id,0,false);}}>최신본 조회 · 입력 비교</button><button disabled={busy} onClick={()=>setPending(undefined)}>서버 상태 비교 완료 · 재전송 없이 계속</button></>}</section>}
      <h3>변경 이력</h3>{history?.items.map(event=><details key={event.id}><summary>{event.action} · {event.reason??'과거 기록'} · {display(event.afterValue?.actorUsername??`관리자 #${event.actorId}`)} · {event.occurredAt}</summary><p>변경 전</p><AuditSnapshot value={event.beforeValue}/><p>변경 후</p><AuditSnapshot value={event.afterValue}/></details>)}
      {history&&!history.items.length&&<p>변경 이력이 없습니다.</p>}<button disabled={disabled||historyPage===0} onClick={()=>setHistoryPage(n=>n-1)}>이전 이력 페이지</button><button disabled={disabled||!history?.hasNext} onClick={()=>setHistoryPage(n=>n+1)}>다음 이력 페이지</button>
    </>}
    {leaving&&createPortal(<div className="registration-dialog-backdrop"><section role="dialog" aria-modal="true" aria-labelledby="correction-leave-title" className="registration-review" onKeyDown={event=>{
      if(event.key==='Escape')setLeaving(null);
      if(event.key==='Tab') {const buttons=event.currentTarget.querySelectorAll<HTMLButtonElement>('button');if(event.shiftKey&&document.activeElement===buttons[0]){event.preventDefault();buttons[buttons.length-1].focus();}else if(!event.shiftKey&&document.activeElement===buttons[buttons.length-1]){event.preventDefault();buttons[0].focus();}}
    }}><h3 id="correction-leave-title">저장하지 않은 정정 제안이 있습니다</h3><button autoFocus onClick={()=>setLeaving(null)}>계속 작성</button><button onClick={()=>{const next=leaving;setLeaving(null);next();}}>저장하지 않고 나가기</button></section></div>,document.body)}
  </section>;
}
