import { useEffect, useRef, useState } from 'react';

import { Icon } from '@/components/common';
import { openDocumentViaPicker } from '@/state/actions';

import { closeTab, switchTab } from './actions';
import { useWorkspaceStore } from './store';

/**
 * The open-documents strip (shown once two or more PDFs are open). Follows the
 * WAI-ARIA tabs pattern: one tab stop, arrow keys move between tabs, Delete
 * closes the focused one; middle-click closes too.
 */
export function TabBar() {
  const tabs = useWorkspaceStore((s) => s.tabs);
  const activeId = useWorkspaceStore((s) => s.activeId);
  const listRef = useRef<HTMLDivElement>(null);
  const [canScrollLeft, setCanScrollLeft] = useState(false);
  const [canScrollRight, setCanScrollRight] = useState(false);

  useEffect(() => {
    const list = listRef.current;
    if (!list) return;
    const updateScrollButtons = () => {
      setCanScrollLeft(list.scrollLeft > 1);
      setCanScrollRight(list.scrollLeft + list.clientWidth < list.scrollWidth - 1);
    };
    list.addEventListener('scroll', updateScrollButtons, { passive: true });
    const observer = new ResizeObserver(updateScrollButtons);
    observer.observe(list);
    updateScrollButtons();
    return () => {
      list.removeEventListener('scroll', updateScrollButtons);
      observer.disconnect();
    };
  }, [tabs]);

  if (tabs.length === 0) return null;

  const focusTab = (id: string) => {
    const tab = listRef.current?.querySelector<HTMLElement>(`[data-tab-id="${id}"]`);
    tab?.focus();
    tab?.scrollIntoView({ block: 'nearest', inline: 'nearest' });
  };

  const scrollTabs = (direction: -1 | 1) =>
    listRef.current?.scrollBy({ left: direction * 220, behavior: 'smooth' });

  const onKeyDown = (e: React.KeyboardEvent, index: number) => {
    const move = (to: number) => {
      const target = tabs[(to + tabs.length) % tabs.length];
      focusTab(target.id);
      void switchTab(target.id);
    };
    if (e.key === 'ArrowRight') move(index + 1);
    else if (e.key === 'ArrowLeft') move(index - 1);
    else if (e.key === 'Home') move(0);
    else if (e.key === 'End') move(tabs.length - 1);
    else if (e.key === 'Delete') void closeTab(tabs[index].id);
    else return;
    e.preventDefault();
  };

  return (
    <div className="vnyl-tabbar">
      <button
        type="button"
        className="vnyl-icon-button vnyl-tabbar__scroll"
        aria-label="Scroll document tabs left"
        title="Scroll tabs left"
        disabled={!canScrollLeft}
        onClick={() => scrollTabs(-1)}
      >
        <Icon name="chevron-left" size={15} />
      </button>
      <div ref={listRef} className="vnyl-tabbar__list" role="tablist" aria-label="Open documents">
        {tabs.map((tab, i) => {
          const active = tab.id === activeId;
          return (
            <div key={tab.id} className={`vnyl-tab${active ? ' is-active' : ''}`}>
              <button
                type="button"
                role="tab"
                data-tab-id={tab.id}
                aria-selected={active}
                tabIndex={active ? 0 : -1}
                className="vnyl-tab__button"
                title={tab.name}
                onClick={() => void switchTab(tab.id)}
                onAuxClick={(e) => {
                  if (e.button === 1) void closeTab(tab.id);
                }}
                onKeyDown={(e) => onKeyDown(e, i)}
              >
                <Icon name="file" size={14} />
                <span className="vnyl-tab__name">
                  {tab.name.replace(/\.pdf$/i, '').replace(/_/g, ' ')}
                </span>
                {tab.edited && <span className="vnyl-tab__dot" aria-label="edited" />}
              </button>
              <button
                type="button"
                className="vnyl-tab__close"
                aria-label={`Close ${tab.name}`}
                tabIndex={-1}
                onClick={() => void closeTab(tab.id)}
              >
                <Icon name="x" size={12} />
              </button>
            </div>
          );
        })}
      </div>
      <button
        type="button"
        className="vnyl-icon-button vnyl-tabbar__scroll"
        aria-label="Scroll document tabs right"
        title="Scroll tabs right"
        disabled={!canScrollRight}
        onClick={() => scrollTabs(1)}
      >
        <Icon name="chevron-right" size={15} />
      </button>
      <button
        type="button"
        className="vnyl-icon-button"
        aria-label="Open another document"
        title="Open another document"
        onClick={() => void openDocumentViaPicker()}
      >
        <Icon name="plus" size={16} />
      </button>
    </div>
  );
}
