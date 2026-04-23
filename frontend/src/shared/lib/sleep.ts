export const sleep = (ms = 300) =>
  new Promise((resolve) => window.setTimeout(resolve, ms));
