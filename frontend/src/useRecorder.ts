import { useCallback, useEffect, useRef, useState } from 'react';
import { blobToWav } from './wav';

export type RecorderState = 'idle' | 'recording' | 'encoding';

const MAX_SECONDS = 90;

/** Records from the microphone and hands the result to `onRecorded` as a WAV blob. */
export function useRecorder(onRecorded: (wav: Blob) => void) {
  const [state, setState] = useState<RecorderState>('idle');
  const [seconds, setSeconds] = useState(0);
  const [error, setError] = useState<string | null>(null);

  const recorder = useRef<MediaRecorder | null>(null);
  const timer = useRef<number | undefined>(undefined);
  const unmounted = useRef(false);
  const callback = useRef(onRecorded);

  useEffect(() => {
    callback.current = onRecorded;
  }, [onRecorded]);

  const stop = useCallback(() => {
    if (recorder.current?.state === 'recording') recorder.current.stop();
  }, []);

  useEffect(() => {
    unmounted.current = false;
    return () => {
      unmounted.current = true;
      window.clearInterval(timer.current);
      if (recorder.current?.state === 'recording') recorder.current.stop();
    };
  }, []);

  useEffect(() => {
    if (state === 'recording' && seconds >= MAX_SECONDS) stop();
  }, [state, seconds, stop]);

  const start = useCallback(async () => {
    setError(null);
    if (!navigator.mediaDevices?.getUserMedia || typeof MediaRecorder === 'undefined') {
      setError('This browser cannot record audio. Upload an audio file instead.');
      return;
    }
    let stream: MediaStream;
    try {
      stream = await navigator.mediaDevices.getUserMedia({ audio: true });
    } catch {
      setError('Microphone access was denied or no microphone was found.');
      return;
    }

    const chunks: Blob[] = [];
    const rec = new MediaRecorder(stream);
    rec.ondataavailable = (event) => {
      if (event.data.size > 0) chunks.push(event.data);
    };
    rec.onstop = async () => {
      window.clearInterval(timer.current);
      stream.getTracks().forEach((track) => track.stop());
      if (unmounted.current) return;
      setState('encoding');
      try {
        if (chunks.length === 0) throw new Error('empty recording');
        callback.current(await blobToWav(new Blob(chunks, { type: rec.mimeType })));
      } catch {
        setError('Could not process the recording. Try again, or upload a wav/mp3 file.');
      } finally {
        if (!unmounted.current) setState('idle');
      }
    };

    recorder.current = rec;
    rec.start();
    setSeconds(0);
    setState('recording');
    timer.current = window.setInterval(() => setSeconds((s) => s + 1), 1000);
  }, []);

  return { state, seconds, error, start, stop };
}
