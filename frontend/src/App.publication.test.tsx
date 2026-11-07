import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import App from './App';
import type { ApiOptions } from './api';
import type { Restaurant } from './types';
const { api }=vi.hoisted(()=>({api:vi.fn<(path:string,options?:ApiOptions)=>Promise<unknown>>()}));
vi.mock('./api',async original=>({...await original<typeof import('./api')>(),api}));
vi.mock('./KakaoMap',()=>({default:({restaurants}:{restaurants:Restaurant[]})=><div data-testid="publication-map">{restaurants.map(r=>r.name).join(',')}</div>}));
vi.mock('./features/admin/AdminWorkspace',()=>({default:({onPublished}:{onPublished:(isCurrent?:()=>boolean)=>Promise<void>})=><>
  <button onClick={()=>void onPublished().catch(()=>{})}>공개 제어 성공</button>
  <button onClick={()=>void onPublished(()=>false).catch(()=>{})}>이전 계정 갱신</button>
</>}));
function deferred<T>() {let resolve!:(value:T)=>void;let reject!:(error:unknown)=>void;const promise=new Promise<T>((yes,no)=>{resolve=yes;reject=no;});return {promise,resolve,reject};}
const restaurant:Restaurant={id:1,kakaoPlaceId:'123',name:'마지막 공개 식당',roadAddress:'주소',address:null,longitude:127,latitude:37,placeUrl:'https://place.map.kakao.com/123',mentions:[{mentionId:1,chefName:'중단 셰프',videoId:'11111111111',videoTitle:'출처',mentionType:'VISITED',timestampSeconds:0}]};
beforeEach(()=>{vi.resetAllMocks();window.history.replaceState(null,'','/');api.mockImplementation(async path=>path==='/session'?{authenticated:true,username:'admin'}:[]);});
afterEach(cleanup);
it('removes old public memory immediately and never restores it after a failed read or a late initial response',async()=>{
  const initial=deferred<Restaurant[]>(),refresh=deferred<Restaurant[]>();let reads=0;
  api.mockImplementation(async path=>path==='/restaurants'?(++reads===1?initial.promise:refresh.promise):path==='/session'?{authenticated:true,username:'admin'}:[]);
  render(<App/>);fireEvent.click(screen.getByRole('button',{name:'관리자'}));fireEvent.click(await screen.findByRole('button',{name:'공개 제어 성공'}));
  await act(async()=>refresh.reject(new Error('공개 재조회 실패')));
  await act(async()=>initial.resolve([restaurant]));
  fireEvent.click(screen.getByRole('button',{name:'맛집 지도'}));
  expect(screen.queryByRole('heading',{name:restaurant.name})).not.toBeInTheDocument();
  expect(screen.getByTestId('publication-map')).toBeEmptyDOMElement();
  expect(screen.getByRole('alert')).toHaveTextContent('공개 재조회 실패');
  api.mockResolvedValueOnce([]);fireEvent.click(screen.getByRole('button',{name:'다시 시도'}));await screen.findByText('첫 단골집을 기다리고 있어요');
  expect(api.mock.calls.filter(([,options])=>options?.method==='POST')).toHaveLength(0);
});
it('clears a previously displayed last-source marker and search result before the refresh completes',async()=>{
  const refresh=deferred<Restaurant[]>();let reads=0;
  api.mockImplementation(async path=>path==='/restaurants'?(++reads===1?[restaurant]:refresh.promise):path==='/session'?{authenticated:true,username:'admin'}:[]);
  render(<App/>);await screen.findByRole('heading',{name:restaurant.name});
  fireEvent.change(screen.getByLabelText('식당, 지역, 셰프 검색'),{target:{value:'중단 셰프'}});expect(screen.getByTestId('publication-map')).toHaveTextContent(restaurant.name);
  fireEvent.click(screen.getByRole('button',{name:'관리자'}));fireEvent.click(await screen.findByRole('button',{name:'공개 제어 성공'}));
  fireEvent.click(screen.getByRole('button',{name:'맛집 지도'}));
  expect(screen.queryByRole('heading',{name:restaurant.name})).not.toBeInTheDocument();expect(screen.getByTestId('publication-map')).toBeEmptyDOMElement();
  await act(async()=>refresh.resolve([]));expect(screen.getByTestId('publication-map')).toBeEmptyDOMElement();
});
it('accepts only the latest response generation and discards an invalidated account response',async()=>{
  const older=deferred<Restaurant[]>(),latest=deferred<Restaurant[]>();let reads=0;
  api.mockImplementation(async path=>path==='/restaurants'?(++reads===1?[]:reads===2?older.promise:latest.promise):path==='/session'?{authenticated:true,username:'admin'}:[]);
  render(<App/>);await screen.findByText('첫 단골집을 기다리고 있어요');fireEvent.click(screen.getByRole('button',{name:'관리자'}));
  const trigger=await screen.findByRole('button',{name:'공개 제어 성공'});fireEvent.click(trigger);fireEvent.click(trigger);
  await waitFor(()=>expect(reads).toBe(3));await act(async()=>latest.resolve([]));await act(async()=>older.resolve([restaurant]));
  fireEvent.click(screen.getByRole('button',{name:'맛집 지도'}));expect(screen.queryByRole('heading',{name:restaurant.name})).not.toBeInTheDocument();
  api.mockImplementation(async path=>path==='/restaurants'?[restaurant]:{authenticated:true,username:'admin'});
  fireEvent.click(screen.getByRole('button',{name:'관리자'}));fireEvent.click(await screen.findByRole('button',{name:'이전 계정 갱신'}));
  await act(async()=>{});fireEvent.click(screen.getByRole('button',{name:'맛집 지도'}));expect(screen.queryByRole('heading',{name:restaurant.name})).not.toBeInTheDocument();
});
