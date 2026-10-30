<script>
export default {
  name: "TextGreen",
  props: {
    textSpan: {
      type: String,
      default: ""
    }
  }
}
</script>

<template>
  <span v-if="textSpan" class="span-style">{{ textSpan }}</span>
</template>

<style scoped lang="scss">
.span-style {
  /* Mobile (mặc định, cả dọc lẫn ngang) */
  display: block;
  width: 100%;
  box-sizing: border-box;
  padding-left: 0.5rem;
  margin-top: 0.25rem;
  font-size: 12px;
  line-height: 1.4;
  color: #16A34A;
  text-align: left;
  word-break: break-word;

  /* Tablet */
  @media only screen and (min-width: 768px) and (max-width: 1023px) {
    font-size: 14px;
    padding-left: 0.75rem;
  }

  /* Desktop */
  @media (min-width: 1024px) {
    font-size: 16px;
    padding-left: 1rem;
  }

  /* Mobile xoay ngang: đặt CUỐI CÙNG để ghi đè tablet/desktop */
  @media only screen and (max-height: 500px) and (orientation: landscape) {
    font-size: 12px;
    padding-left: 0.5rem;
  }
}
</style>
