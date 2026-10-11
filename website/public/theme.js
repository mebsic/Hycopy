try {
  const theme = localStorage.getItem('hycopy-theme');
  if (theme === 'dark' || theme === 'light') document.documentElement.dataset.theme = theme;
} catch { /* Storage is optional. */ }
