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
  <!--
    Khi loading: chữ được ẩn (visibility: hidden, vẫn giữ chỗ nên nút không bị đổi kích thước)
    và spinner nằm chính giữa nút. Spinner tự viết trong button-red.scss,
    không dùng .spinner-border của Bootstrap.
  -->
  <button
      class="button-red"
      :class="['button-red-opacity-disabled', setOpacityBtnLogin]"
      :disabled="disableButton"
      :aria-busy="loadingButton"
      :aria-label="loadingButton ? 'Đang xử lý' : null"
  >
    <span class="button-red__text" :class="{ 'is-hidden': loadingButton }">{{ textButton }}</span>
    <span v-if="loadingButton" class="button-red__spinner" aria-hidden="true"></span>
  </button>
</template>