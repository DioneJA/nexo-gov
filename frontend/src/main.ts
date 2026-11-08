import { createApp } from 'vue'
import {
  QBanner, QBtn, QCard, QCardActions, QCardSection, QDialog, QFile, QForm, QHeader, QIcon, QInput, QLayout, QPage,
  QPageContainer, QSelect, QSeparator, QSpace, QTab, QTabPanel, QTabPanels, QTabs, QTree, Quasar
} from 'quasar'
import 'quasar/dist/quasar.css'
import '@quasar/extras/material-icons/material-icons.css'
import '@nexo/core-ui/tokens.css'
import './style.css'
import App from './App.vue'

createApp(App).use(Quasar, {
  components: {
    QBanner, QBtn, QCard, QCardActions, QCardSection, QDialog, QFile, QForm, QHeader, QIcon, QInput, QLayout, QPage,
    QPageContainer, QSelect, QSeparator, QSpace, QTab, QTabPanel, QTabPanels, QTabs, QTree
  },
  config: { brand: { primary: '#07194f', secondary: '#07194f', accent: '#0051d5' } }
}).mount('#app')
