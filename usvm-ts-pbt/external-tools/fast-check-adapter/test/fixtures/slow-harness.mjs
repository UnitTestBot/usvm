export async function invoke() {
  await new Promise((resolve) => setTimeout(resolve, 10));
  return true;
}
