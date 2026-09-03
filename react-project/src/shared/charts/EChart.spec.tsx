import { render } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'

const chart = vi.hoisted(() => ({
  setOption: vi.fn(),
  resize: vi.fn(),
  dispose: vi.fn(),
}))
const init = vi.hoisted(() => vi.fn(() => chart))
const register = vi.hoisted(() => vi.fn())

vi.mock('echarts/core', () => ({ init, use: register }))
vi.mock('echarts/charts', () => ({ BarChart: {}, LineChart: {} }))
vi.mock('echarts/components', () => ({
  GridComponent: {},
  LegendComponent: {},
  MarkLineComponent: {},
  TitleComponent: {},
  TooltipComponent: {},
}))
vi.mock('echarts/renderers', () => ({ CanvasRenderer: {} }))

import { EChart } from './EChart'

describe('EChart', () => {
  it('creates one chart instance, applies new options and disposes on unmount', () => {
    const first = { xAxis: { type: 'category' }, series: [] }
    const second = { xAxis: { type: 'value' }, series: [] }
    const rendered = render(<EChart ariaLabel="测试图表" option={first} />)

    expect(register).toHaveBeenCalledOnce()
    expect(init).toHaveBeenCalledOnce()
    expect(chart.setOption).toHaveBeenLastCalledWith(first, { notMerge: true, lazyUpdate: true })

    rendered.rerender(<EChart ariaLabel="测试图表" option={second} />)
    expect(init).toHaveBeenCalledOnce()
    expect(chart.setOption).toHaveBeenLastCalledWith(second, { notMerge: true, lazyUpdate: true })

    rendered.unmount()
    expect(chart.dispose).toHaveBeenCalledOnce()
  })
})
