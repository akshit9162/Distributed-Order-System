import { useState } from 'react';

export default function CreateAuction({ onCreate }) {
  const [form, setForm] = useState({ title: 'Vintage film camera', startPrice: '10.00', durationSeconds: '120' });
  const field = (name) => ({ name, value: form[name], onChange: (e) => setForm({ ...form, [name]: e.target.value }) });

  return (
    <div className="panel">
      <h2>List an item</h2>
      <form onSubmit={(e) => { e.preventDefault(); onCreate(form); }}>
        <label>Title <input {...field('title')} required maxLength={120} /></label>
        <div className="row">
          <label>Start price <input {...field('startPrice')} type="number" min="0.01" step="0.01" required /></label>
          <label>Ends in (seconds) <input {...field('durationSeconds')} type="number" min="5" max="604800" required /></label>
        </div>
        <button type="submit">Start auction</button>
      </form>
    </div>
  );
}
