import { describe, expect, it } from 'vitest';
import { changedValues, emptyFields, timestampSeconds, values } from './registrationInput';
describe('registration input contract', () => {
  it.each([['',null],['00:00',0],['03:24',204],['1:02:03',3723]])('converts %s without inventing a timestamp', (input, expected) => {
    expect(timestampSeconds(input)).toBe(expected);
  });
  it.each(['-1:00','1:60','1:60:00','1','1:2:3:4','999999999999:00'])('rejects invalid timestamp %s', input => {
    expect(() => timestampSeconds(input)).toThrow();
  });
  it('sends explicit nulls and only changed PATCH fields', () => {
    const baseline=values(emptyFields());
    expect(baseline).toEqual({chefId:null,videoId:null,mentionType:null,timestampSeconds:null,verificationNote:null});
    expect(changedValues(baseline,{...baseline,chefId:2,verificationNote:'old'})).toEqual({chefId:null,verificationNote:null});
    expect(changedValues(baseline,baseline)).toEqual({});
    expect(() => values({...emptyFields(),note:' '.repeat(2001)})).toThrow();
  });
});
