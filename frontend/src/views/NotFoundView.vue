<script setup lang="ts">
import { RouterLink } from 'vue-router'
</script>

<template>
  <section>
    <h1>Страница не найдена</h1>
    <p>Проверьте адрес или вернитесь на главную страницу.</p>
    <RouterLink :to="{ name: 'home' }">На главную</RouterLink>
  </section>
</template>
