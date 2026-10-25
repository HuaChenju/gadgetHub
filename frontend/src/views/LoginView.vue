<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ApiError, NetworkError } from '../api/client'
import { auth } from '../state/auth'

const router = useRouter()
const login = ref('')
const password = ref('')
const isSubmitting = ref(false)
const fieldErrors = ref<Record<string, string>>({})
const errorMessage = ref('')
const loginInput = ref<HTMLInputElement | null>(null)
const passwordInput = ref<HTMLInputElement | null>(null)
const errorDialog = ref<HTMLDialogElement | null>(null)

function focusInvalidField() {
  if (fieldErrors.value.login) loginInput.value?.focus()
  else if (fieldErrors.value.password) passwordInput.value?.focus()
}

async function submit() {
  if (isSubmitting.value) return
  fieldErrors.value = {}
  errorMessage.value = ''
  if (!login.value.trim()) fieldErrors.value.login = 'Заполните обязательное поле'
  if (!password.value.trim()) fieldErrors.value.password = 'Заполните обязательное поле'
  if (Object.keys(fieldErrors.value).length) {
    focusInvalidField()
    return
  }

  isSubmitting.value = true
  try {
    await auth.login(login.value, password.value)
    password.value = ''
    await router.push({ name: 'home' })
  } catch (error) {
    if (error instanceof ApiError && error.code === 'VALIDATION_ERROR'
      && (error.fieldErrors.login || error.fieldErrors.password)) {
      fieldErrors.value = error.fieldErrors
      focusInvalidField()
    } else {
      errorMessage.value = error instanceof ApiError || error instanceof NetworkError
        ? error.message
        : 'Не удалось войти. Попробуйте позже.'
      errorDialog.value?.showModal()
    }
  } finally {
    isSubmitting.value = false
  }
}
</script>

<template>
  <section class="login-page" aria-labelledby="login-title">
    <div class="login-content">
      <h1 id="login-title">Добро пожаловать!</h1>
      <form class="login-form" novalidate :aria-busy="isSubmitting" @submit.prevent="submit">
        <div class="form-field">
          <label for="login">Логин</label>
          <span class="required-mark" aria-hidden="true">*</span>
          <input
            id="login"
            ref="loginInput"
            v-model="login"
            name="login"
            type="text"
            autocomplete="username"
            required
            :disabled="isSubmitting"
            :aria-invalid="Boolean(fieldErrors.login)"
            :aria-describedby="fieldErrors.login ? 'login-error' : undefined"
            @input="delete fieldErrors.login"
          />
          <p v-if="fieldErrors.login" id="login-error" class="field-error">{{ fieldErrors.login }}</p>
        </div>
        <div class="form-field">
          <label for="password">Пароль</label>
          <span class="required-mark" aria-hidden="true">*</span>
          <input
            id="password"
            ref="passwordInput"
            v-model="password"
            name="password"
            type="password"
            autocomplete="current-password"
            required
            :disabled="isSubmitting"
            :aria-invalid="Boolean(fieldErrors.password)"
            :aria-describedby="fieldErrors.password ? 'password-error' : undefined"
            @input="delete fieldErrors.password"
          />
          <p v-if="fieldErrors.password" id="password-error" class="field-error">{{ fieldErrors.password }}</p>
        </div>
        <button class="login-button" type="submit" :disabled="isSubmitting">
          {{ isSubmitting ? 'Входим…' : 'Войти' }}
        </button>
      </form>
    </div>

    <dialog ref="errorDialog" class="login-dialog" aria-labelledby="login-error-title">
      <h2 id="login-error-title">Не удалось войти</h2>
      <p role="alert">{{ errorMessage }}</p>
      <button class="login-button" type="button" @click="errorDialog?.close()">Понятно</button>
    </dialog>
  </section>
</template>

<style scoped>
.login-page {
  min-height: 940px;
  padding: 192px 24px 80px;
  background: #e5eeff url('../assets/login-background.png') center 28px / 1900px 915px no-repeat;
}

.login-content {
  width: 382px;
  max-width: 100%;
  margin-inline: auto;
}

h1 {
  margin-bottom: 32px;
  font-size: 32px;
  line-height: 42px;
  text-align: center;
}

.login-form {
  display: grid;
  gap: 32px;
  padding: 48px;
  border-radius: 8px;
  background: #fff;
}

.form-field {
  position: relative;
}

.form-field label {
  display: block;
  margin-bottom: 4px;
  font-size: 14px;
  line-height: 21px;
}

.required-mark {
  position: absolute;
  top: 17px;
  left: calc(100% + 2px);
  width: 8px;
  height: 8px;
  overflow: hidden;
  font-size: 0;
  background: url('../assets/icons/required.svg') center / contain no-repeat;
}

.form-field input {
  width: 100%;
  height: 48px;
  padding: 10px 12px;
  border: 1px solid transparent;
  border-radius: 8px;
  background: #e4e7eb;
  color: inherit;
  font: inherit;
}

.form-field input:focus-visible {
  outline: 2px solid #165dff;
  outline-offset: 2px;
}

.form-field input[aria-invalid='true'] {
  border-color: #c52b35;
}

.field-error {
  margin: 8px 0 0;
  color: #c52b35;
  font-size: 12px;
}

.login-button {
  justify-self: start;
  min-height: 48px;
  padding: 12px 32px;
  border: 0;
  border-radius: 8px;
  background: #115efb;
  color: #fff;
  font-weight: 500;
}

.login-button:hover:not(:disabled) {
  background: #104bcf;
}

.login-button:active:not(:disabled) {
  background: #0d3eae;
}

.login-button:disabled {
  opacity: 0.6;
}

.login-dialog {
  width: 440px;
  max-width: calc(100% - 48px);
  padding: 32px;
  border: 0;
  border-radius: 12px;
  color: inherit;
}

.login-dialog::backdrop {
  background: rgb(15 23 42 / 45%);
}

.login-dialog h2 {
  margin: 0;
  font-size: 22px;
}
</style>
