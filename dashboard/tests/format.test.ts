import { describe, expect, it } from 'vitest';
import {
  formatCustomValue,
  formatDistanceCm,
  formatTicks,
  humanizeKey,
  namespaceOf,
  relativeTime,
  statTypeLabel,
} from '../src/lib/format';

describe('humanizeKey', () => {
  it('strips namespace and title-cases', () => {
    expect(humanizeKey('minecraft:play_time')).toBe('Play Time');
    expect(humanizeKey('minecraft:diamond_sword')).toBe('Diamond Sword');
    expect(humanizeKey('mymod:some.nested/key')).toBe('Some Nested Key');
  });
});

describe('namespaceOf', () => {
  it('defaults to minecraft', () => {
    expect(namespaceOf('stone')).toBe('minecraft');
    expect(namespaceOf('create:cogwheel')).toBe('create');
  });
});

describe('formatTicks', () => {
  it('formats durations from ticks', () => {
    expect(formatTicks(0)).toBe('0s');
    expect(formatTicks(20 * 60)).toBe('1m');
    expect(formatTicks(20 * 60 * 60)).toBe('1h');
    expect(formatTicks(20 * 60 * 60 * 25)).toBe('1d 1h');
  });
});

describe('formatDistanceCm', () => {
  it('switches between metres and kilometres', () => {
    expect(formatDistanceCm(150)).toBe('1.5 m');
    expect(formatDistanceCm(250000)).toBe('2.50 km');
  });
});

describe('formatCustomValue', () => {
  it('uses time formatting for time keys', () => {
    expect(formatCustomValue('minecraft:play_time', 20 * 60)).toBe('1m');
  });
  it('uses distance formatting for distance keys', () => {
    expect(formatCustomValue('minecraft:walk_one_cm', 100000)).toBe('1.00 km');
  });
  it('falls back to plain numbers', () => {
    expect(formatCustomValue('minecraft:jump', 1234)).toBe((1234).toLocaleString());
  });
});

describe('statTypeLabel', () => {
  it('humanizes the type id', () => {
    expect(statTypeLabel('minecraft:mined')).toBe('Mined');
  });
});

describe('relativeTime', () => {
  it('handles null and recent times', () => {
    expect(relativeTime(null)).toBe('unknown');
    expect(relativeTime(Date.now())).toBe('just now');
    expect(relativeTime(Date.now() - 2 * 60 * 1000)).toBe('2m ago');
  });
});
