<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { AlertCircle, ChevronDown, ChevronUp, Eye, EyeOff } from '@lucide/vue'
import { useAuthStore } from '@/stores/auth.store'

const router = useRouter()
const auth = useAuthStore()
const email = ref('')
const password = ref('')
const showPassword = ref(false)
const showDemo = ref(false)
const localError = ref('')

const demoUsers = [
  ['admin@dikoin.local', 'ADMIN'],
  ['tecnico@dikoin.local', 'TECNICO'],
  ['revisor@dikoin.local', 'REVISOR'],
  ['cliente@dikoin.local', 'CLIENTE'],
]

async function submit() {
  if (!email.value || !password.value) {
    localError.value = 'Introduce email y contraseña.'
    return
  }
  localError.value = ''
  try {
    const user = await auth.login(email.value, password.value)
    router.push({ name: user.role === 'CLIENTE' ? 'portal' : 'dashboard' })
  } catch {
    // El store ya deja el mensaje exacto en auth.error.
  }
}

function fillDemo(mail: string) {
  email.value = mail
  password.value = '1234'
  showDemo.value = false
}
</script>

<template>
  <div class="login-page">
    <div class="login-box">
      <div class="login-brand">
        <div class="brand-mark">DK</div>
        <h1>Manuales</h1>
        <p>DIKOIN</p>
      </div>

      <form class="login-card" @submit.prevent="submit">
        <h2>Acceso</h2>
        <div v-if="localError || auth.error" class="login-error">
          <AlertCircle :size="15" />
          {{ localError || auth.error }}
        </div>

        <label>
          Correo electrónico
          <input v-model="email" class="field" type="email" placeholder="tecnico@dikoin.local" />
        </label>
        <label>
          Contraseña
          <span class="password-field">
            <input v-model="password" class="field" :type="showPassword ? 'text' : 'password'" placeholder="1234" />
            <button type="button" @click="showPassword = !showPassword">
              <EyeOff v-if="showPassword" :size="15" />
              <Eye v-else :size="15" />
            </button>
          </span>
        </label>
        <button class="btn btn-primary login-submit" :disabled="auth.loading">
          {{ auth.loading ? 'Accediendo...' : 'Entrar' }}
        </button>
      </form>

      <div class="demo-card">
        <button class="demo-toggle" @click="showDemo = !showDemo">
          Credenciales del backend
          <ChevronUp v-if="showDemo" :size="14" />
          <ChevronDown v-else :size="14" />
        </button>
        <div v-if="showDemo" class="demo-list">
          <button v-for="[mail, role] in demoUsers" :key="mail" @click="fillDemo(mail)">
            <span class="mono">{{ mail }}</span>
            <small>{{ role }} · contraseña 1234</small>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.login-page {
  min-height: 100%;
  display: grid;
  place-items: center;
  background: var(--dikoin-blue-dark);
}
.login-box { width: min(380px, calc(100vw - 32px)); }
.login-brand { text-align: center; color: #fff; margin-bottom: 24px; }
.brand-mark {
  width: 56px;
  height: 56px;
  margin: 0 auto 14px;
  display: grid;
  place-items: center;
  background: var(--dikoin-blue);
  border-radius: 5px;
  font-weight: 600;
  font-size: 20px;
}
.login-brand h1 { margin: 0; font-size: 22px; }
.login-brand p { margin: 6px 0 0; color: #c7e4f6; }
.login-card {
  background: #fff;
  padding: 28px;
  border-radius: var(--radius);
  display: grid;
  gap: 15px;
  box-shadow: 0 20px 60px rgba(0,0,0,.28);
}
.login-card h2 { margin: 0 0 6px; font-size: 17px; }
label { display: grid; gap: 6px; font-size: 13px; font-weight: 600; }
.password-field { position: relative; display: block; }
.password-field input { padding-right: 38px; }
.password-field button {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
  border: 0;
  background: transparent;
  color: var(--muted-foreground);
}
.login-submit { width: 100%; padding: 10px; }
.login-error {
  display: flex;
  align-items: center;
  gap: 7px;
  color: #991b1b;
  background: #fef2f2;
  border: 1px solid #fecaca;
  padding: 9px;
  border-radius: var(--radius);
  font-size: 13px;
}
.demo-card { margin-top: 14px; border: 1px solid rgba(255,255,255,.18); background: rgba(0,0,0,.12); }
.demo-toggle {
  width: 100%;
  display: flex;
  justify-content: space-between;
  padding: 10px 12px;
  color: #d7edf8;
  background: transparent;
  border: 0;
}
.demo-list { border-top: 1px solid rgba(255,255,255,.18); display: grid; }
.demo-list button {
  display: flex;
  justify-content: space-between;
  text-align: left;
  border: 0;
  background: transparent;
  color: #fff;
  padding: 9px 12px;
}
.demo-list button:hover { background: rgba(255,255,255,.08); }
.demo-list small { color: #c7e4f6; }
</style>
