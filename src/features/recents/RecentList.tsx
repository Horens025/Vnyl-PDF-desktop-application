import { useState } from 'react';

import { openRecent } from './openRecent';
import { folderOf, timeAgo, useRecentsStore } from './store';

/** The "Recent" list on the start screen. Empty (and invisible) until a file has been opened from disk. */
export function RecentList() {
  const items = useRecentsStore((s) => s.items);
  const clear = useRecentsStore((s) => s.clear);
  const [now] = useState(() => Date.now());
  if (items.length === 0) return null;

  return (
    <section className="vnyl-recents" aria-labelledby="recents-heading">
      <div className="vnyl-recents__head">
        <h2 id="recents-heading">Recent</h2>
        <button type="button" className="vnyl-recents__clear" onClick={clear}>
          Clear
        </button>
      </div>
      <ul>
        {items.map((file) => (
          <li key={file.path}>
            <button
              type="button"
              className="vnyl-recents__item"
              onClick={() => void openRecent(file)}
              title={file.path}
            >
              <span className="vnyl-recents__name">{file.name}</span>
              <span className="vnyl-recents__meta">
                {folderOf(file.path)} · {timeAgo(file.openedAt, now)}
              </span>
            </button>
          </li>
        ))}
      </ul>
    </section>
  );
}
