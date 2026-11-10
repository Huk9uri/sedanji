import React from 'react';
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import App from './App';
import type { ApiOptions } from './api';
import type { Area, Bounds, Item } from './features/explore/contracts';
const { api }=vi.hoisted(()=>({api:vi.fn<(path:string,options?:ApiOptions)=>Promise<unknown>>()}));
vi.mock('./api',async original=>({...await original<typeof import('./api')>(),api}));
vi.mock('./features/explore/ExploreMap',()=>({default:({items,onReady}:{items:Item[];onReady:(b:Bounds,failed:boolean)=>void})=>{
  React.useEffect(()=>onReady({west:126.96,east:126.99,south:37.55,north:37.58},true),[onReady]);
  return <div data-testid="publication-map">{items.map(r=>r.name).join(',')}</div>;
}}));
vi.mock('./features/admin/AdminWorkspace',()=>({default:({onPublished}:{onPublished:(isCurrent?:()=>boolean)=>Promise<void>})=><>
  <button onClick={()=>void onPublished().catch(()=>{})}>공개 제어 성공</button>
  <button onClick={()=>void onPublished(()=>false).catch(()=>{})}>이전 계정 갱신</button>
</>}));
function deferred<T>() {let resolve!:(value:T)=>void;let reject!:(error:unknown)=>void;const promise=new Promise<T>((yes,no)=>{resolve=yes;reject=no;});return {promise,resolve,reject};}
const restaurant:Item={id:1,name:'마지막 공개 식당',roadAddress:'주소',address:null,longitude:126.97,latitude:37.56,chefs:[],chefCount:1,hasMoreChefs:false};
const empty:Area={items:[],query:{west:126.96,east:126.99,south:37.55,north:37.58,chefId:null},returnedCount:0,limit:200,truncated:false,totalCount:null};
function respond(read:()=>Promise<Area>|Area) {
  api.mockImplementation(async path=>path.startsWith('/public/restaurants?')?read():path==='/session'?{authenticated:true,username:'admin'}:{items:[],page:0,size:50,totalElements:0,hasNext:false});
}
async function start() {render(<App/>);fireEvent.click(screen.getByRole('button',{name:'서울 시청 기본 지역'}));await waitFor(()=>expect(api.mock.calls.some(([p])=>p.startsWith('/public/restaurants?'))).toBe(true));}
async function admin() {fireEvent.click(screen.getByRole('button',{name:'관리자'}));return screen.findByRole('button',{name:'공개 제어 성공'});}
beforeEach(()=>{vi.resetAllMocks();window.history.replaceState(null,'','/');});
afterEach(cleanup);
it('invalidates a pending initial read and keeps it invalid after refresh failure',async()=>{
  const initial=deferred<Area>(),refresh=deferred<Area>();let reads=0;
  respond(()=>(++reads===1?initial.promise:refresh.promise)); await start();fireEvent.click(await admin());
  await act(async()=>refresh.reject(new Error('공개 재조회 실패')));await act(async()=>initial.resolve({...empty,items:[restaurant],returnedCount:1}));
  expect(screen.getByTestId('publication-map')).toBeEmptyDOMElement();
  expect(api.mock.calls.filter(([,options])=>options?.method==='POST')).toHaveLength(0);
});
it('clears a previously displayed last-source marker immediately on successful admin mutation',async()=>{
  const refresh=deferred<Area>();let reads=0;respond(()=>(++reads===1?{...empty,items:[restaurant],returnedCount:1}:refresh.promise));
  await start();await waitFor(()=>expect(screen.getByTestId('publication-map')).toHaveTextContent(restaurant.name));
  fireEvent.click(await admin());expect(screen.getByTestId('publication-map')).toBeEmptyDOMElement();
  await act(async()=>refresh.resolve(empty));expect(screen.getByTestId('publication-map')).toBeEmptyDOMElement();
});
it('discards older refreshes and responses from an invalidated account',async()=>{
  const older=deferred<Area>(),latest=deferred<Area>();let reads=0;respond(()=>(++reads===1?empty:reads===2?older.promise:latest.promise));
  await start();await screen.findByText('검색 결과가 없습니다');const trigger=await admin();fireEvent.click(trigger);fireEvent.click(trigger);
  await waitFor(()=>expect(reads).toBe(3));await act(async()=>latest.resolve(empty));await act(async()=>older.resolve({...empty,items:[restaurant],returnedCount:1}));
  expect(screen.getByTestId('publication-map')).toBeEmptyDOMElement();
  respond(()=>({...empty,items:[restaurant],returnedCount:1}));fireEvent.click(screen.getByRole('button',{name:'이전 계정 갱신'}));
  await act(async()=>{});expect(screen.getByTestId('publication-map')).toBeEmptyDOMElement();
});
