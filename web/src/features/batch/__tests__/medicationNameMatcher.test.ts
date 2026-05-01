import { describe, expect, it } from 'vitest';
import {
  matchMedicationsInCommand,
  normalize,
  type Medication,
} from '../medicationNameMatcher';

const WELLBUTRIN: Medication = {
  id: '10',
  name: 'Wellbutrin XL',
  display_label: 'Bupropion',
};
const ADDERALL: Medication = { id: '20', name: 'Adderall' };
const TWO_WELLBUTRINS: Medication[] = [
  { id: '30', name: 'Wellbutrin' },
  { id: '31', name: 'Wellbutrin' },
];

describe('medicationNameMatcher (twin-checked vs Kotlin/Python)', () => {
  it('exact match returns unambiguous', () => {
    expect(
      matchMedicationsInCommand('I took my Wellbutrin XL today', [
        WELLBUTRIN,
        ADDERALL,
      ]),
    ).toEqual({ kind: 'unambiguous', matches: { 'wellbutrin xl': '10' } });
  });

  it('case mismatch normalizes', () => {
    expect(matchMedicationsInCommand('WELLBUTRIN xl', [WELLBUTRIN])).toEqual({
      kind: 'unambiguous',
      matches: { 'wellbutrin xl': '10' },
    });
  });

  it('trailing whitespace normalizes', () => {
    expect(matchMedicationsInCommand('  took adderall   ', [ADDERALL])).toEqual({
      kind: 'unambiguous',
      matches: { adderall: '20' },
    });
  });

  it('unicode smart quote normalizes', () => {
    expect(matchMedicationsInCommand('“took adderall”', [ADDERALL])).toEqual({
      kind: 'unambiguous',
      matches: { adderall: '20' },
    });
  });

  it('typo returns no match', () => {
    expect(matchMedicationsInCommand('took wellbutrn', [WELLBUTRIN])).toEqual({
      kind: 'no_match',
    });
  });

  it('two wellbutrins returns ambiguous', () => {
    const result = matchMedicationsInCommand('took my Wellbutrin', TWO_WELLBUTRINS);
    expect(result).toEqual({
      kind: 'ambiguous',
      phrases: [{ phrase: 'wellbutrin', candidate_entity_ids: ['30', '31'] }],
    });
  });

  it('display label match when name does not', () => {
    expect(
      matchMedicationsInCommand('finished bupropion today', [WELLBUTRIN]),
    ).toEqual({ kind: 'unambiguous', matches: { bupropion: '10' } });
  });

  it('mixed command with one unambiguous and one ambiguous returns mixed', () => {
    const result = matchMedicationsInCommand(
      'took my Wellbutrin and Adderall',
      [ADDERALL, ...TWO_WELLBUTRINS],
    );
    expect(result).toEqual({
      kind: 'mixed',
      unambiguous: { adderall: '20' },
      ambiguous: [{ phrase: 'wellbutrin', candidate_entity_ids: ['30', '31'] }],
    });
  });

  it('trailing punctuation is stripped', () => {
    expect(matchMedicationsInCommand('took my Adderall.', [ADDERALL])).toEqual({
      kind: 'unambiguous',
      matches: { adderall: '20' },
    });
  });

  it('longest key wins on overlap', () => {
    const plain: Medication = { id: '100', name: 'Wellbutrin' };
    const xl: Medication = { id: '101', name: 'Wellbutrin XL' };
    expect(matchMedicationsInCommand('took my Wellbutrin XL', [plain, xl])).toEqual({
      kind: 'unambiguous',
      matches: { 'wellbutrin xl': '101' },
    });
  });

  it('substring inside another word does not match', () => {
    expect(matchMedicationsInCommand('took my addy', [ADDERALL])).toEqual({
      kind: 'no_match',
    });
  });

  it('empty command returns no match', () => {
    expect(matchMedicationsInCommand('', [ADDERALL])).toEqual({ kind: 'no_match' });
    expect(matchMedicationsInCommand('   ', [ADDERALL])).toEqual({ kind: 'no_match' });
  });

  it('empty medication list returns no match', () => {
    expect(matchMedicationsInCommand('took adderall', [])).toEqual({ kind: 'no_match' });
  });

  it('normalize is idempotent', () => {
    const once = normalize('  Wellbutrin XL!  ');
    const twice = normalize(once);
    expect(once).toBe('wellbutrin xl');
    expect(twice).toBe(once);
  });
});
