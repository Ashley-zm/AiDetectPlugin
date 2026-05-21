<template>
  <view class="page">
    <view class="panel">
      <text class="title">AiDetectPlugin</text>

      <button class="button" type="default" @click="callTest">调用 test</button>
      <button class="button" type="primary" @click="startDetect">打开 AI 检测页面</button>
      <button class="button" type="default" @click="startDetectSync">同步打开检测页面</button>

      <text class="status">{{ status }}</text>
      <text class="result">{{ resultText }}</text>
    </view>
  </view>
</template>

<script>
const aiDetectPlugin = uni.requireNativePlugin('AiDetectPlugin')

export default {
  data() {
    return {
      status: '待调用',
      resultText: ''
    }
  },
  methods: {
    callTest() {
      if (!this.ensurePluginMethod('test')) {
        return
      }

      this.status = '调用 test 中'
      aiDetectPlugin.test(
        {
          from: 'pages/detect/detect.vue'
        },
        (res) => {
          this.status = res && res.success ? 'test 调用成功' : 'test 返回异常'
          this.resultText = JSON.stringify(res, null, 2)
          console.log('AiDetectPlugin.test result:', res)
        }
      )
    },

    startDetect() {
      if (!this.ensurePluginMethod('startDetect')) {
        return
      }

      const options = {
        modelType: 'object_detect',
        engine: 'native',
        modelName: 'demo_model',
        threshold: 0.5,
        detectInterval: 500,
        inputSize: 320
      }

      this.status = '打开 DetectActivity 中'
      const syncResult = aiDetectPlugin.startDetect(options, (res) => {
        this.status = res && res.success ? 'DetectActivity 已打开' : '打开失败'
        this.resultText = JSON.stringify(res, null, 2)
        console.log('AiDetectPlugin.startDetect result:', res)
      })
      console.log('AiDetectPlugin.startDetect sync return:', syncResult)
      if (syncResult) {
        this.resultText = JSON.stringify(syncResult, null, 2)
      }
    },

    startDetectSync() {
      if (!this.ensurePluginMethod('startDetectSync')) {
        return
      }

      const options = {
        modelType: 'object_detect',
        engine: 'native',
        modelName: 'demo_model_sync',
        threshold: 0.6,
        detectInterval: 800,
        inputSize: 320
      }

      this.status = '同步打开 DetectActivity 中'
      const res = aiDetectPlugin.startDetectSync(options)
      this.status = res && res.success ? 'DetectActivity 已打开' : '打开失败'
      this.resultText = JSON.stringify(res, null, 2)
      console.log('AiDetectPlugin.startDetectSync result:', res)
    },

    ensurePluginMethod(methodName) {
      if (!aiDetectPlugin || typeof aiDetectPlugin[methodName] !== 'function') {
        this.status = '插件未加载'
        this.resultText = '请确认已在 manifest.json 勾选本地插件，并使用自定义基座运行。'
        return false
      }
      return true
    }
  }
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 32rpx;
  background: #f5f7fb;
  box-sizing: border-box;
}

.panel {
  padding: 32rpx;
  border-radius: 8rpx;
  background: #ffffff;
}

.title {
  display: block;
  margin-bottom: 24rpx;
  color: #1f2937;
  font-size: 36rpx;
  font-weight: 600;
}

.button {
  margin: 0 0 24rpx;
}

.status {
  display: block;
  margin-bottom: 16rpx;
  color: #2563eb;
  font-size: 28rpx;
}

.result {
  display: block;
  padding: 24rpx;
  border-radius: 8rpx;
  background: #111827;
  color: #e5e7eb;
  font-family: monospace;
  font-size: 24rpx;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
