<script>
import './button-blue.scss';
export default {
  name: "ButtonBlue",
  props: {
    textButton: {
      required: true,
      type: String,
      default: null,
    },

    loadingButton: {
      required: true,
      type: Boolean,
      default: false,
    },

    disableButton: {
      required: true,
      type: Boolean,
      default: false,
    }
  },

  computed: {
    setOpacityBtnLogin() {
      return (this.loadingButton === true) ? 'opacity-disabled' : 'opacity-allow';
    }

    // setBorderColorChoose() {
    //   return (index) => {
    //     return (index === this.indexQuestion)
    //         ? 'border-choose'
    //         : 'border-no-choose';
    //   };
    // },
  },
}
</script>

<template>
<!--   -->
  <button class="button-blue" :disabled="disableButton"
          :class="['button-blue-opacity-disabled', setOpacityBtnLogin]"
         >
    {{ textButton }}
    <div v-if="loadingButton" class="spinner-border text-light style-icon-spinner" role="status"/>
      <!-- <span class="sr-only">Loading...</span>-->
  </button>
</template>

<style scoped lang="scss">
.style-icon-spinner {
  width: 1.65rem;
  height: 1.65rem;
}
</style>