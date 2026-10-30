export function animateAdminDialog(dialog: HTMLDialogElement, opening: boolean, reducedMotion: boolean, drawer: boolean) {
  let cancelled = false;
  let animation: Animation | undefined;
  const wasOpen = dialog.open;
  dialog.dataset.closing = String(!opening);
  if (opening && !dialog.open) dialog.showModal();
  if (!opening && !dialog.open) return () => {};
  if (reducedMotion || typeof dialog.animate !== 'function') {
    dialog.style.opacity = '1';
    dialog.style.transform = 'none';
    if (!opening) dialog.close();
  } else {
    // Drawer slides with solid opacity so the panel edge is distinctly visible gliding into view;
    // Modal enters with vertical rise, subtle scale, and smooth opacity.
    const enterDuration = drawer ? 280 : 230;
    const exitDuration = drawer ? 200 : 160;
    const enterEasing = 'cubic-bezier(0.16, 1, 0.3, 1)'; // ease.soft / natural fluid deceleration
    const exitEasing = 'cubic-bezier(0.3, 0, 0.8, 0.15)';  // swift exit
    const offset = drawer ? 'translateX(100%)' : 'translateY(18px) scale(.97)';
    const visible = dialog.ownerDocument.defaultView?.getComputedStyle(dialog);
    const initialOpacity = drawer ? '1' : '0';
    const from = wasOpen
      ? { opacity: visible?.opacity || '1', transform: visible?.transform || 'none' }
      : { opacity: initialOpacity, transform: offset };
    const to = opening
      ? { opacity: '1', transform: 'none' }
      : { opacity: drawer ? '1' : '0', transform: offset };
    const duration = opening ? enterDuration : exitDuration;
    const easing = opening ? enterEasing : exitEasing;
    animation = dialog.animate([from, to], { duration, easing, fill: 'both' });
    if (!opening) void animation.finished.then(() => { if (!cancelled && dialog.open) dialog.close(); }).catch(() => {});
  }
  return () => {
    cancelled = true;
    // Preserve the visible frame when a quick reversal cancels an unfinished transition.
    if (animation && dialog.open) {
      const visible = dialog.ownerDocument.defaultView?.getComputedStyle(dialog);
      dialog.style.opacity = visible?.opacity || '1';
      dialog.style.transform = visible?.transform || 'none';
    }
    animation?.cancel();
  };
}
