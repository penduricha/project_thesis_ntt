<script>
import './button-red.scss';
export default {
  name: "ButtonRed",
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
  <button class="button-red" :disabled="disableButton"
          :class="['button_red-opacity-disabled', setOpacityBtnLogin]"
         >
    {{ textButton }}
    <div v-if="loadingButton" class="spinner-border text-light style-icon-spinner" role="status"/>
      <!-- <span class="sr-only">Loading...</span>-->
  </button>
</template>

<style scoped lang="scss">
.style-icon-spinner {
  width: 1.2rem;
  height: 1.2rem;
}
</style>