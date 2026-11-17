/** `preventDefault` on mousedown keeps the page text selection alive while a
    button is pressed, but would stop a slider from being dragged. */
export function keepSelection(e: React.MouseEvent): void {
  if ((e.target as HTMLElement).tagName !== 'INPUT') e.preventDefault();
}
