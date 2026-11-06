import { createApp } from 'vue'
import {
  QBanner, QBtn, QCard, QForm, QHeader, QIcon, QInput, QLayout, QPage,
  QPageContainer, QSelect, QSeparator, QSpace, QTab, QTabPanel, QTabPanels, QTabs, Quasar
} from 'quasar'
import 'quasar/dist/quasar.css'
import '@quasar/extras/material-icons/material-icons.css'
import './style.css'
import App from './App.vue'

createApp(App).use(Quasar, {
  components: {
    QBanner, QBtn, QCard, QForm, QHeader, QIcon, QInput, QLayout, QPage,
    QPageContainer, QSelect, QSeparator, QSpace, QTab, QTabPanel, QTabPanels, QTabs
  },
  config: { brand: { primary: '#176b64', secondary: '#2f4858', accent: '#d08b43' } }
}).mount('#app')
