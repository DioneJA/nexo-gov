import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath } from 'node:url'

export default defineConfig({
  plugins: [vue()],
  server: { port: Number(process.env.NEXOGOV_WEB_PORT || process.env.NEXOGOV_E2E_WEB_PORT || 9000) },
  build: {
    rolldownOptions: {
      input: {
        citizen: fileURLToPath(new URL('./index.html', import.meta.url)),
        staff: fileURLToPath(new URL('./servidor/index.html', import.meta.url))
      }
    }
  }
})
