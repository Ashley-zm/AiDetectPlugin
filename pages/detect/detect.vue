<template>
  <view class="page">
    <view class="panel">
      <text class="title">AiDetectPlugin</text>

      <button class="button" type="default" @click="callTest">调用 test</button>
      <button class="button" type="primary" @click="startDetect">打开 AI 检测页面</button>
      <button class="button" type="default" @click="startDetectSync">同步打开检测页面</button>
      <button class="button" type="default" @click="takeSnapshot">拍照并结束检测</button>
      <button class="button" type="warn" @click="stopDetect">停止检测</button>

      <text class="status">{{ status }}</text>
      <text v-if="snapshotPath" class="snapshot-path">照片路径：{{ snapshotPath }}</text>

      <view class="summary">
        <view class="summary-item">
          <text class="summary-label">是否检测到目标</text>
          <text class="summary-value" :class="{ active: hasTarget }">{{ hasTarget ? '是' : '否' }}</text>
        </view>
        <view class="summary-item">
          <text class="summary-label">检测框数量</text>
          <text class="summary-value">{{ boxCount }}</text>
        </view>
        <view class="summary-item">
          <text class="summary-label">最高置信度</text>
          <text class="summary-value">{{ maxScoreText }}</text>
        </view>
      </view>

      <view class="target-list">
        <text class="section-title">检测目标列表</text>
        <view v-if="boxes.length === 0" class="empty">
          <text class="empty-text">暂无目标</text>
        </view>
        <view v-for="(box, index) in boxes" :key="index" class="target-item">
          <view class="target-row">
            <text class="target-name">{{ box.label || ('class_' + box.classId) }}</text>
            <text class="target-score">{{ formatScore(box.score) }}</text>
          </view>
          <text class="target-meta">
            #{{ box.classId }}  left {{ formatNumber(box.left) }}  top {{ formatNumber(box.top) }}
            right {{ formatNumber(box.right) }}  bottom {{ formatNumber(box.bottom) }}
          </text>
        </view>
      </view>

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
      resultText: '',
      hasTarget: false,
      boxCount: 0,
      maxScore: 0,
      boxes: [],
      lastResultAt: 0,
      snapshotPath: ''
    }
  },
  computed: {
    maxScoreText() {
      return this.boxCount > 0 ? this.formatScore(this.maxScore) : '0.00'
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

      this.resetDetectState()

      const options = {
        pipelineMode: true,
        detectInterval: 200,
        callbackInterval: 500,
        targetModel: {
          modelType: 'detection',
          engine: 'ncnn',
          modelName: 'yolov8n',
          modelPath: 'models/yolov8n_ncnn/yolov8n.param',
          binPath: 'models/yolov8n_ncnn/yolov8n.bin',
          labelPath: 'models/yolov8n_ncnn/labels.txt',
          inputSize: 640,
          threshold: 0.5,
          iouThreshold: 0.45,
          useGpu: false
        }
      }

      this.status = '打开 DetectActivity 中'
      const syncResult = aiDetectPlugin.startDetect(options, (res) => {
        this.handleDetectCallback(res)
      })
      console.log('AiDetectPlugin.startDetect sync return:', syncResult)
      if (syncResult) {
        this.handleDetectCallback(syncResult)
      }
    },

    startDetectSync() {
      if (!this.ensurePluginMethod('startDetectSync')) {
        return
      }

      this.resetDetectState()

      const options = {
        pipelineMode: true,
        detectInterval: 500,
        callbackInterval: 500,
        targetModel: {
          modelType: 'detection',
          engine: 'ncnn',
          modelName: 'yolov8n',
          modelPath: 'models/yolov8n_ncnn/yolov8n.param',
          binPath: 'models/yolov8n_ncnn/yolov8n.bin',
          labelPath: 'models/yolov8n_ncnn/labels.txt',
          inputSize: 640,
          threshold: 0.5,
          iouThreshold: 0.45,
          useGpu: false
        }
      }

      this.status = '同步打开 DetectActivity 中'
      const res = aiDetectPlugin.startDetectSync(options)
      this.status = res && res.success ? 'DetectActivity 已打开' : '打开失败'
      this.resultText = JSON.stringify(res, null, 2)
      console.log('AiDetectPlugin.startDetectSync result:', res)
    },

    takeSnapshot() {
      if (!this.ensurePluginMethod('takeSnapshot')) {
        return
      }

      this.status = '拍照中'
      const res = aiDetectPlugin.takeSnapshot(
        {},
        (snapshotRes) => {
          console.log('AiDetectPlugin.takeSnapshot result:', snapshotRes)
          if (snapshotRes && snapshotRes.success) {
            this.snapshotPath = snapshotRes.imagePath || ''
            this.status = '拍照完成，检测页面已关闭'
          } else {
            this.status = snapshotRes && snapshotRes.message ? `拍照失败：${snapshotRes.message}` : '拍照失败'
          }
          this.resultText = JSON.stringify(snapshotRes, null, 2)
        }
      )
      console.log('AiDetectPlugin.takeSnapshot sync return:', res)
      if (res) {
        this.resultText = JSON.stringify(res, null, 2)
      }
    },

    stopDetect() {
      if (!this.ensurePluginMethod('stopDetect')) {
        return
      }

      const res = aiDetectPlugin.stopDetect({}, (stopRes) => {
        console.log('AiDetectPlugin.stopDetect result:', stopRes)
        this.status = stopRes && stopRes.success ? '检测已停止' : '停止检测失败'
        this.resultText = JSON.stringify(stopRes, null, 2)
      })
      console.log('AiDetectPlugin.stopDetect sync return:', res)
      if (res) {
        this.status = res.success ? '检测已停止' : '停止检测失败'
        this.resultText = JSON.stringify(res, null, 2)
      }
    },

    handleDetectCallback(res) {
      console.log('AiDetectPlugin.startDetect callback:', res)

      if (!res) {
        this.status = '回调为空'
        return
      }

      if (res.type === 'detect_result') {
        const boxes = Array.isArray(res.boxes) ? res.boxes : []
        this.hasTarget = !!res.hasTarget
        this.boxes = boxes
        this.boxCount = boxes.length
        this.maxScore = boxes.reduce((max, box) => Math.max(max, Number(box.score) || 0), 0)
        this.lastResultAt = res.timestamp || Date.now()
        this.status = res.message || (this.hasTarget ? '检测通过' : '未检测到目标')
        this.resultText = JSON.stringify(res, null, 2)
        return
      }

      if (res.type === 'snapshot') {
        const boxes = Array.isArray(res.boxes) ? res.boxes : []
        this.snapshotPath = res.imagePath || ''
        this.hasTarget = !!res.hasTarget
        this.boxes = boxes
        this.boxCount = boxes.length
        this.maxScore = boxes.reduce((max, box) => Math.max(max, Number(box.score) || 0), 0)
        this.status = res.message || '拍照完成，检测页面已关闭'
        this.resultText = JSON.stringify(res, null, 2)
        return
      }

      if (res.type === 'snapshot_error') {
        this.status = res.message ? `拍照失败：${res.message}` : '拍照失败'
        this.resultText = JSON.stringify(res, null, 2)
        return
      }

      if (res.type === 'error' || res.success === false) {
        this.status = res.message ? `检测错误：${res.message}` : '检测错误'
        this.resultText = JSON.stringify(res, null, 2)
        return
      }

      if (res.type === 'activity_opened') {
        this.status = 'DetectActivity 已打开，等待检测结果'
      } else if (res.type === 'camera_preview_started') {
        this.status = '相机预览已启动，等待检测结果'
      } else {
        this.status = res.message || '检测流程更新'
      }
      this.resultText = JSON.stringify(res, null, 2)
    },

    resetDetectState() {
      this.hasTarget = false
      this.boxCount = 0
      this.maxScore = 0
      this.boxes = []
      this.lastResultAt = 0
      this.snapshotPath = ''
      this.resultText = ''
    },

    formatScore(value) {
      const score = Number(value) || 0
      return score.toFixed(2)
    },

    formatNumber(value) {
      const number = Number(value) || 0
      return number.toFixed(1)
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
  margin-bottom: 20rpx;
  color: #2563eb;
  font-size: 28rpx;
}

.snapshot-path {
  display: block;
  margin-bottom: 20rpx;
  color: #4b5563;
  font-size: 24rpx;
  word-break: break-all;
}

.summary {
  display: flex;
  margin-bottom: 24rpx;
  gap: 16rpx;
}

.summary-item {
  flex: 1;
  padding: 20rpx;
  border: 1rpx solid #e5e7eb;
  border-radius: 8rpx;
  background: #f9fafb;
}

.summary-label {
  display: block;
  margin-bottom: 8rpx;
  color: #6b7280;
  font-size: 22rpx;
}

.summary-value {
  display: block;
  color: #111827;
  font-size: 32rpx;
  font-weight: 600;
}

.summary-value.active {
  color: #16a34a;
}

.target-list {
  margin-bottom: 24rpx;
}

.section-title {
  display: block;
  margin-bottom: 12rpx;
  color: #374151;
  font-size: 28rpx;
  font-weight: 600;
}

.empty {
  padding: 24rpx;
  border: 1rpx dashed #d1d5db;
  border-radius: 8rpx;
  background: #f9fafb;
}

.empty-text {
  color: #9ca3af;
  font-size: 26rpx;
}

.target-item {
  padding: 20rpx;
  margin-bottom: 16rpx;
  border: 1rpx solid #e5e7eb;
  border-radius: 8rpx;
  background: #ffffff;
}

.target-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8rpx;
}

.target-name {
  color: #111827;
  font-size: 28rpx;
  font-weight: 600;
}

.target-score {
  color: #16a34a;
  font-size: 28rpx;
  font-weight: 600;
}

.target-meta {
  color: #6b7280;
  font-size: 22rpx;
  line-height: 1.5;
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
