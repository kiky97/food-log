import { describe, expect, it } from 'vitest';
import { encodeWav } from '../wav';

const ascii = (view: DataView, offset: number, length: number) =>
  Array.from({ length }, (_, i) => String.fromCharCode(view.getUint8(offset + i))).join('');

describe('encodeWav', () => {
  it('writes a valid 16 kHz mono 16-bit PCM header', () => {
    const view = new DataView(encodeWav(new Float32Array(100), 16000));

    expect(ascii(view, 0, 4)).toBe('RIFF');
    expect(ascii(view, 8, 4)).toBe('WAVE');
    expect(ascii(view, 12, 4)).toBe('fmt ');
    expect(view.getUint16(20, true)).toBe(1); // PCM
    expect(view.getUint16(22, true)).toBe(1); // mono
    expect(view.getUint32(24, true)).toBe(16000);
    expect(view.getUint32(28, true)).toBe(32000); // byte rate
    expect(view.getUint16(34, true)).toBe(16);
    expect(ascii(view, 36, 4)).toBe('data');
    expect(view.getUint32(40, true)).toBe(200);
    expect(view.byteLength).toBe(44 + 200);
    expect(view.getUint32(4, true)).toBe(view.byteLength - 8);
  });

  it('converts samples to signed 16-bit and clips out-of-range values', () => {
    const view = new DataView(encodeWav(new Float32Array([0, 1, -1, 2, -2, 0.5]), 16000));
    const sample = (i: number) => view.getInt16(44 + i * 2, true);

    expect(sample(0)).toBe(0);
    expect(sample(1)).toBe(32767);
    expect(sample(2)).toBe(-32768);
    expect(sample(3)).toBe(32767);
    expect(sample(4)).toBe(-32768);
    expect(sample(5)).toBe(16383);
  });
});
