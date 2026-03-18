import Vue from 'vue'
import App from './App'
import config from './common/config.js'
import squni from './util/squni.js'

Vue.config.productionTip = false
Vue.prototype.$config = config
Vue.prototype.$squni = squni

App.mpType = 'app'

const app = new Vue({
  ...App
})

app.$mount()
