import { mergeConfig } from 'vite'
import baseConfig from './vite.config.base'
import configCompressPlugin from './plugin/compress'
import configVisualizerPlugin from './plugin/visualizer'
import configImageminPlugin from './plugin/imagemin'

export default mergeConfig(
  {
    mode: 'production', // vite生产模式
    // 插件的具体配置请查看对应的文件
    // gzip + brotli 双格式预压缩：Caddy file_server precompressed 优先发 br（同级别比 gzip 再小 15~20%）
    plugins: [
      configCompressPlugin('gzip'),
      configCompressPlugin('brotli'),
      configVisualizerPlugin(),
      configImageminPlugin(),
    ],
    build: {
      rollupOptions: {
        output: {
          // 分包策略优化
          manualChunks: {
            vue: ['vue', 'vue-router', 'pinia', '@vueuse/core'],
            xgplayer: ['xgplayer'],
          },
        },
      },
      chunkSizeWarningLimit: 2000,
    },
  },
  baseConfig,
)
