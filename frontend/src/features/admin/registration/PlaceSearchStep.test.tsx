import { afterEach, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen, act } from '@testing-library/react';
import PlaceSearchStep from './PlaceSearchStep';
import { registrationApi as api } from './registrationApi';
import type { PlaceSearch } from './types';
vi.mock('./registrationApi',()=>({registrationApi:{places:vi.fn()}}));
afterEach(()=>{cleanup();vi.resetAllMocks();});
it('ignores an older search arriving after the current result',async()=>{
  let first!:(value:PlaceSearch)=>void;
  vi.mocked(api.places).mockImplementationOnce(()=>new Promise(resolve=>{first=resolve;})).mockResolvedValueOnce({candidates:[],totalCount:0,complete:true});
  render(<PlaceSearchStep onSelect={vi.fn()} onStop={vi.fn()} onFailure={vi.fn()} disabled={false}/>);
  fireEvent.change(screen.getByLabelText('식당 이름 검색'),{target:{value:'첫 검색'}});fireEvent.click(screen.getByRole('button',{name:'장소 검색'}));
  fireEvent.change(screen.getByLabelText('식당 이름 검색'),{target:{value:'현재 검색'}});fireEvent.click(screen.getByRole('button',{name:'장소 검색'}));
  await screen.findByText(/검색 결과가 없습니다/);
  await act(async()=>first({candidates:[{placeSelectionToken:'old',place:{id:'1',name:'과거 장소',address:null,roadAddress:null,longitude:127,latitude:37,placeUrl:'https://place.map.kakao.com/1'}}],totalCount:1,complete:true}));
  expect(screen.queryByText('과거 장소')).not.toBeInTheDocument();expect(screen.getByText(/검색 결과가 없습니다/)).toBeInTheDocument();
});
