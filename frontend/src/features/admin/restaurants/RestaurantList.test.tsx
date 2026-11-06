import { useState } from 'react';
import { afterEach, expect, it, vi } from 'vitest';
import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import RestaurantList from './RestaurantList';
import { initialConditions } from './types';
import type { ListConditions } from './types';

afterEach(cleanup);
function mount(conditions = initialConditions(), loading = false, error?: unknown) {
  const execute = vi.fn();
  function Harness() {
    const [input, setInput] = useState('');
    const [executed, setExecuted] = useState(conditions);
    return <RestaurantList input={input} onInput={setInput} conditions={executed}
      onConditions={(next: ListConditions) => { execute(next); setExecuted(next); }}
      data={{ items: [], totalElements: 0, page: executed.page, size: 20, hasNext: false }}
      loading={loading} error={error} onRetry={vi.fn()} onOpen={vi.fn()} onNew={vi.fn()} onCandidates={vi.fn()}/>;
  }
  render(<Harness/>);
  return execute;
}
it.each(['가', '😀'])('accepts 100 and rejects 101 code points using %s through both submit paths', character => {
  const execute = mount({ ...initialConditions(), query: '기존', page: 2 });
  const input = screen.getByLabelText('관리자 식당 검색');
  fireEvent.change(input, { target: { value: `  ${character.repeat(100)}  ` } });
  fireEvent.click(screen.getByRole('button', { name: '검색' }));
  expect(execute).toHaveBeenLastCalledWith({ ...initialConditions(), query: character.repeat(100), page: 0 });
  fireEvent.change(input, { target: { value: character.repeat(101) } });
  // Enter and the search button both submit this same form.
  fireEvent.submit(input.closest('form')!);
  expect(screen.getByRole('alert')).toHaveTextContent('검색어는 100자 이하여야 합니다.');
  expect(execute).toHaveBeenCalledTimes(1);
  expect(screen.getByText('전체 0곳 · 1페이지')).toBeInTheDocument();
});
it('preserves executed conditions and page when the first submit is invalid', () => {
  const execute = mount({ ...initialConditions(), query: '기존 검색', publication: 'PUBLISHED', page: 2 });
  fireEvent.change(screen.getByLabelText('관리자 식당 검색'), { target: { value: '😀'.repeat(101) } });
  fireEvent.click(screen.getByRole('button', { name: '검색' }));
  expect(execute).not.toHaveBeenCalled();
  expect(screen.getByText('전체 0곳 · 3페이지')).toBeInTheDocument();
  expect(screen.getByLabelText('공개 관계 필터')).toHaveValue('PUBLISHED');
});
it('uses executed rather than currently typed conditions to explain empty results', () => {
  mount();
  expect(screen.getByText('등록된 식당이 없습니다.')).toBeInTheDocument();
  fireEvent.change(screen.getByLabelText('관리자 식당 검색'), { target: { value: '아직 실행 안 함' } });
  expect(screen.getByText('등록된 식당이 없습니다.')).toBeInTheDocument();
  fireEvent.click(screen.getByRole('button', { name: '검색' }));
  expect(screen.getByText('조건에 맞는 식당이 없습니다.')).toBeInTheDocument();
  fireEvent.change(screen.getByLabelText('관리자 식당 검색'), { target: { value: '' } });
  expect(screen.getByText('조건에 맞는 식당이 없습니다.')).toBeInTheDocument();
});
it.each([{ publication: 'PUBLISHED' as const }, { needsSourceReview: true }])('explains filter-only empty results: %j', filter => {
  mount({ ...initialConditions(), ...filter });
  expect(screen.getByText('조건에 맞는 식당이 없습니다.')).toBeInTheDocument();
  expect(screen.queryByText('등록된 식당이 없습니다.')).not.toBeInTheDocument();
});
it.each([true, false])('never presents loading or failed reads as empty results (loading=%s)', loading => {
  mount(initialConditions(), loading, loading ? undefined : new Error('목록 실패'));
  expect(screen.queryByText('등록된 식당이 없습니다.')).not.toBeInTheDocument();
  expect(screen.queryByText('조건에 맞는 식당이 없습니다.')).not.toBeInTheDocument();
  expect(screen.getByRole(loading ? 'status' : 'alert')).toHaveTextContent(loading ? '불러오는 중' : '목록 실패');
});
