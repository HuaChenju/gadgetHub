<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { auth } from './state/auth'
import { cart, CART_STORAGE_KEY } from './state/cart'
import { ApiError, NetworkError } from './api/client'
import catalogIcon from './assets/icons/catalog.svg'
import profileIcon from './assets/icons/profile.svg'
import mobileIcon from './assets/icons/mobile.svg'
import vkIcon from './assets/social/vk.png'
import telegramIcon from './assets/social/telegram.png'
import whatsappIcon from './assets/social/whatsapp.png'

const { state: authState, restoreSession } = auth
const route = useRoute()
const router = useRouter()
const logoutError = ref('')
const cartBadge = computed(() => {
  if (cart.state.isLoading) return '…'
  if (cart.state.error) return '!'
  return cart.state.totalQuantity ? String(cart.state.totalQuantity) : null
})
const cartLabel = computed(() => {
  if (cart.state.isLoading) return 'Корзина, обновляем количество товаров'
  if (cart.state.error) return 'Корзина, не удалось обновить количество товаров'
  return `Корзина, количество товаров: ${cart.state.totalQuantity ?? 0}`
})

watch(auth.isAuthenticated, signedIn => {
  if (signedIn) void cart.restore()
  else cart.reset()
}, { immediate: true })

function syncCart(event: StorageEvent) {
  if (auth.isAuthenticated.value && event.storageArea === window.localStorage
    && (event.key === CART_STORAGE_KEY || event.key === null)) {
    void cart.restore()
  }
}

onMounted(() => window.addEventListener('storage', syncCart))
onUnmounted(() => window.removeEventListener('storage', syncCart))

async function logout() {
  if (authState.isLoggingOut) return
  logoutError.value = ''
  try {
    await auth.logout()
  } catch (error) {
    logoutError.value = error instanceof ApiError || error instanceof NetworkError
      ? error.message
      : 'Не удалось выйти. Попробуйте позже.'
    return
  }
  await router.push({ name: 'home' })
}
</script>

<template>
  <div class="app-layout">
    <header class="app-header">
      <div class="container header-content">
        <RouterLink class="brand" :to="{ name: 'home' }"><span>Gadget</span> Hub</RouterLink>
        <nav aria-label="Основная навигация" :class="{ 'authenticated-nav': authState.user }">
          <RouterLink class="nav-link" :to="{ name: 'catalog' }">
            <img :src="catalogIcon" alt="" width="16" height="16" />Каталог
          </RouterLink>
          <RouterLink v-if="authState.user" class="nav-link cart-link" :to="{ name: 'cart' }" :aria-label="cartLabel">
            <img :src="profileIcon" alt="" width="16" height="16" />Корзина
            <span v-if="cartBadge" class="cart-badge" aria-hidden="true">{{ cartBadge }}</span>
          </RouterLink>
          <button v-if="authState.user" class="nav-link logout-button" type="button"
            :disabled="authState.isLoggingOut" :aria-busy="authState.isLoggingOut" @click="logout">
            <img :src="profileIcon" alt="" width="16" height="16" />Выйти
          </button>
          <RouterLink v-else-if="authState.isInitialized" class="nav-link login-link" :to="{ name: 'login' }">
            <img :src="profileIcon" alt="" width="16" height="16" />Войти
          </RouterLink>
        </nav>
      </div>
    </header>
    <main :class="route.name === 'login' ? 'login-main' : 'container page-content'">
      <div v-if="authState.user && cart.state.error" class="auth-error">
        <p role="alert">{{ cart.state.error }}</p>
        <button type="button" :disabled="cart.state.isLoading" @click="cart.restore">Обновить корзину</button>
      </div>
      <div v-if="logoutError" class="auth-error">
        <p role="alert">{{ logoutError }}</p>
        <button type="button" :disabled="authState.isLoggingOut" @click="logout">Попробовать снова</button>
      </div>
      <div v-if="authState.error" class="auth-error">
        <p role="alert">{{ authState.error }}</p>
        <button type="button" :disabled="authState.isLoading" @click="restoreSession">Повторить</button>
      </div>
      <p v-if="authState.isLoading && !authState.isInitialized" role="status">Проверяем вход…</p>
      <RouterView v-if="authState.isInitialized" />
    </main>
    <footer class="app-footer">
      <div class="container footer-content">
        <div class="footer-brand">
          <RouterLink :to="{ name: 'home' }">Gadget Hub</RouterLink>
          <p>Магазин надежных гаджетов</p>
          <p class="copyright">© 2024 ООО “Гаджет Хаб”. Все права защищены</p>
        </div>
        <a class="footer-phone" href="tel:88006783424">
          <img :src="mobileIcon" alt="" width="22" height="32" />8 (800) 678-34-24
        </a>
        <div class="footer-social" aria-label="Социальные сети">
          <img :src="vkIcon" alt="ВКонтакте" width="36" height="36" />
          <img :src="telegramIcon" alt="Telegram" width="36" height="36" />
          <img :src="whatsappIcon" alt="WhatsApp" width="36" height="36" />
        </div>
      </div>
    </footer>
  </div>
</template>
