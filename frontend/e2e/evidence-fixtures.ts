import { expect, type Locator, type Page, type TestInfo } from '@playwright/test'

const evidenceMode = process.env.NEXOGOV_EVIDENCE_MODE === '1'

function evidenceSlug(value: string) {
  return value.normalize('NFKD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '')
    .slice(0, 72)
}

export async function attachEvidenceScreenshot(
  page: Page,
  testInfo: TestInfo,
  label: string,
  focus?: Locator,
  additionalMasks: Locator[] = []
) {
  if (!evidenceMode) return

  const automaticBearerMasks: Locator[] = []
  const receiptKey = page.getByLabel('Chave do comprovante')
  if (await receiptKey.count()) {
    await expect(receiptKey).toBeVisible()
    const bearer = await receiptKey.inputValue()
    if (bearer) {
      await receiptKey.evaluate(element => {
        (element as HTMLInputElement).type = 'password'
      })
      await expect(receiptKey).toHaveAttribute('type', 'password')
    }
    automaticBearerMasks.push(receiptKey)
  }
  const invitationKeys = page.getByLabel(/^Convite de /)
  for (const invitationKey of await invitationKeys.all()) {
    await expect(invitationKey).toBeVisible()
    automaticBearerMasks.push(invitationKey)
  }
  const masks = [...additionalMasks, ...automaticBearerMasks]

  if (focus) {
    await focus.scrollIntoViewIfNeeded()
    await focus.evaluate(element => {
      const fixedHeaderBottom = Array.from(document.querySelectorAll<HTMLElement>('.q-header, header'))
        .filter(header => {
          const position = window.getComputedStyle(header).position
          return (position === 'fixed' || position === 'sticky') && header.getBoundingClientRect().height > 0
        })
        .reduce((bottom, header) => Math.max(bottom, header.getBoundingClientRect().bottom), 0)
      const focusTop = element.getBoundingClientRect().top
      const adjustment = focusTop - fixedHeaderBottom - 12
      if (fixedHeaderBottom > 0 && adjustment < 0) window.scrollBy(0, adjustment)
    })
    await expect(focus).toBeVisible()
  }

  // Capture the current viewport around the asserted feature. Full-page
  // screenshots duplicate sticky navigation and make long screens unreadable.
  // Bearer fields are configured as password inputs and passed to Playwright's
  // screenshot mask. The encoded PNG bytes cannot prove that pixels are masked;
  // exported images still require visual inspection.
  const screenshot = await page.screenshot({
    fullPage: false,
    mask: masks,
    maskColor: '#17212b'
  })
  expect(screenshot.subarray(0, 8)).toEqual(Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]))
  await testInfo.attach(`nexogov-evidence-${evidenceSlug(label)}`, {
    body: screenshot,
    contentType: 'image/png'
  })
}
