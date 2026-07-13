/// <reference types="vite/client" />

declare module '*.vue' {
  import type { DefineComponent } from 'vue'
  const component: DefineComponent<object, object, unknown>
  export default component
}

declare module 'qrcode' {
  const QRCode: {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    toDataURL(text: string, options?: any): Promise<string>
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    toString(text: string, options?: any): Promise<string>
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    toCanvas(canvas: HTMLCanvasElement, text: string, options?: any): Promise<void>
  }
  export default QRCode
}
