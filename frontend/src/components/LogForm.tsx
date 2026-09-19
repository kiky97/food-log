import { useRef, useState } from 'react';
import type { FormEvent, KeyboardEvent } from 'react';
import { useRecorder } from '../useRecorder';

interface Props {
  busy: boolean;
  /** Resolves true when the entry was saved, so the form can clear itself. */
  onText: (text: string) => Promise<boolean>;
  onAudio: (audio: Blob, filename: string) => Promise<void>;
}

const EXAMPLES = [
  'I had two boiled eggs, a bowl of rice and an apple for breakfast',
  '150g of salmon for lunch',
  'a glass of milk and a banana',
];

export function LogForm({ busy, onText, onAudio }: Props) {
  const [text, setText] = useState('');
  const fileInput = useRef<HTMLInputElement>(null);
  const textarea = useRef<HTMLTextAreaElement>(null);
  const recorder = useRecorder((wav) => void onAudio(wav, 'recording.wav'));
  const recording = recorder.state === 'recording';
  const disabled = busy || recorder.state === 'encoding';

  const submit = async (event?: FormEvent) => {
    event?.preventDefault();
    if (!text.trim() || disabled) return;
    if (await onText(text.trim())) setText('');
  };

  const onKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) void submit();
  };

  const onFile = (file: File | undefined) => {
    if (file) void onAudio(file, file.name);
    if (fileInput.current) fileInput.current.value = '';
  };

  return (
    <form className="card composer" onSubmit={submit}>
      <label htmlFor="log-text" className="composer-label">
        What did you eat?
      </label>
      <textarea
        id="log-text"
        ref={textarea}
        rows={3}
        value={text}
        placeholder="Say or type it naturally, e.g. “two eggs and a bowl of rice for breakfast”"
        onChange={(e) => setText(e.target.value)}
        onKeyDown={onKeyDown}
        disabled={disabled || recording}
        maxLength={2000}
      />

      <div className="examples">
        {EXAMPLES.map((example) => (
          <button key={example} type="button" className="chip" disabled={disabled || recording}
            onClick={() => {
              setText(example);
              textarea.current?.focus();
            }}
          >
            {example}
          </button>
        ))}
      </div>

      <div className="composer-actions">
        <button
          type="button"
          className={`btn btn-mic ${recording ? 'is-recording' : ''}`}
          onClick={recording ? recorder.stop : recorder.start}
          disabled={disabled && !recording}
          aria-pressed={recording}
        >
          <span className="mic-dot" aria-hidden="true" />
          {recording
            ? `Stop · ${String(Math.floor(recorder.seconds / 60)).padStart(2, '0')}:${String(recorder.seconds % 60).padStart(2, '0')}`
            : recorder.state === 'encoding'
              ? 'Processing…'
              : 'Record'}
        </button>

        <button type="button" className="btn btn-quiet" disabled={disabled || recording} onClick={() => fileInput.current?.click()}>
          Upload audio
        </button>
        <input
          ref={fileInput}
          type="file"
          hidden
          accept="audio/*,.txt,text/plain"
          onChange={(e) => onFile(e.target.files?.[0])}
        />

        <button type="submit" className="btn btn-primary" disabled={disabled || recording || !text.trim()}>
          {busy ? 'Logging…' : 'Log meal'}
        </button>
      </div>

      {recorder.error && (
        <p role="alert" className="form-error">
          {recorder.error}
        </p>
      )}
      <p className="hint">Ctrl + Enter to submit. Recordings are sent as 16 kHz WAV.</p>
    </form>
  );
}
