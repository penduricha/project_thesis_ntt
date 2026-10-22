<script>
export default {
  name: "SpanInvalid",
  props: {
    textSpan: {
      type: String,
      default: "",
      required: true
    }
  }
}
</script>

<template>
  <span v-if="textSpan" class="span-style">{{this.textSpan}}</span>
</template>

<style scoped lang="scss">
.span-style {
  font-size: 16px;
  color: #ff2c2c;
  width: 100%;
  text-align: left;
  display: block;
  padding-left: 0.5rem;
}
</style>