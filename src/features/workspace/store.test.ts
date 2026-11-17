import { beforeEach, describe, expect, it } from 'vitest';

import { neighbourAfterClose, useWorkspaceStore, type Tab } from './store';

const tab = (id: string, over: Partial<Tab> = {}): Tab => ({
  id,
  name: `${id}.pdf`,
  fingerprint: `fp-${id}`,
  data: new Uint8Array([1]),
  url: null,
  path: null,
  edited: false,
  page: 1,
  scale: 1,
  fitMode: 'width',
  ...over,
});

describe('workspace store', () => {
  beforeEach(() => useWorkspaceStore.getState().reset());

  it('upsert adds new tabs and replaces existing ones in place', () => {
    const s = useWorkspaceStore.getState();
    s.upsert(tab('a'));
    s.upsert(tab('b'));
    s.upsert(tab('a', { page: 7 }));
    const { tabs } = useWorkspaceStore.getState();
    expect(tabs.map((t) => t.id)).toEqual(['a', 'b']);
    expect(tabs[0].page).toBe(7);
  });

  it('patch updates one tab only', () => {
    const s = useWorkspaceStore.getState();
    s.upsert(tab('a'));
    s.upsert(tab('b'));
    s.patch('b', { scale: 2, edited: true });
    const { tabs } = useWorkspaceStore.getState();
    expect(tabs[0].scale).toBe(1);
    expect(tabs[1]).toMatchObject({ scale: 2, edited: true });
  });

  it('removing the active tab clears activeId', () => {
    const s = useWorkspaceStore.getState();
    s.upsert(tab('a'));
    s.setActive('a');
    s.remove('a');
    expect(useWorkspaceStore.getState().activeId).toBeNull();
  });

  it('neighbourAfterClose prefers the right tab, then the left', () => {
    const tabs = [tab('a'), tab('b'), tab('c')];
    expect(neighbourAfterClose(tabs, 'b')?.id).toBe('c');
    expect(neighbourAfterClose(tabs, 'c')?.id).toBe('b');
    expect(neighbourAfterClose([tab('a')], 'a')).toBeNull();
  });
});
