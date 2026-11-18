import { createApp } from 'vue'
import 'element-plus/dist/index.css'
import App from './App.vue'
import { router } from './router'
import { pinia } from './stores/pinia'
import { permissionDirective } from './utils/permissions'
import './style.css'

const app = createApp(App)
app.use(pinia)
app.use(router)
app.directive('permission', permissionDirective)
app.mount('#app')
