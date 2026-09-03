import { beforeEach, describe, expect, it, vi } from 'vitest'

type ClientConfig = {
  brokerURL: string
  connectHeaders: Record<string, string>
  reconnectDelay: number
  heartbeatIncoming: number
  heartbeatOutgoing: number
  onConnect: () => void
}

const clients = vi.hoisted(
  () =>
    [] as Array<{
      config: ClientConfig
      connected: boolean
      activate: ReturnType<typeof vi.fn>
      deactivate: ReturnType<typeof vi.fn>
      publish: ReturnType<typeof vi.fn>
      subscribe: ReturnType<typeof vi.fn>
    }>,
)

vi.mock('@stomp/stompjs', () => ({
  Client: class MockClient {
    config: ClientConfig
    connected = false
    activate = vi.fn()
    deactivate = vi.fn()
    publish = vi.fn()
    subscribe = vi.fn()
    constructor(config: ClientConfig) {
      this.config = config
      clients.push(this)
    }
  },
}))

import { liveSocket, liveSocketUrl } from './liveSocket'

describe('live STOMP adapter', () => {
  beforeEach(() => {
    liveSocket.disconnect()
    clients.length = 0
  })

  it('authenticates a student, configures reconnect and subscribes only to student topics', () => {
    const onStatus = vi.fn()
    const onEvent = vi.fn()
    liveSocket.connect({ role: 'student', sessionId: 7, token: 'student-token', onStatus, onEvent })
    const client = clients[0]!

    expect(liveSocketUrl({ protocol: 'https:', host: 'example.com' })).toBe(
      'wss://example.com/ws/live',
    )
    expect(client.config.connectHeaders).toEqual({
      'X-Session-Id': '7',
      Authorization: 'Bearer student-token',
    })
    expect(client.config).toMatchObject({
      reconnectDelay: 5_000,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
    })
    expect(client.activate).toHaveBeenCalledOnce()
    expect(onStatus).toHaveBeenCalledWith('connecting')

    client.connected = true
    client.config.onConnect()
    expect(onStatus).toHaveBeenLastCalledWith('connected')
    expect(client.subscribe).toHaveBeenNthCalledWith(
      1,
      '/topic/session/7/interaction',
      expect.any(Function),
    )
    expect(client.subscribe).toHaveBeenNthCalledWith(
      2,
      '/topic/session/7/interaction-timing',
      expect.any(Function),
    )
    expect(client.subscribe).toHaveBeenNthCalledWith(
      3,
      '/topic/session/7/hand-queue',
      expect.any(Function),
    )
    expect(client.subscribe).toHaveBeenNthCalledWith(
      4,
      '/topic/session/7/teacher-status',
      expect.any(Function),
    )

    expect(liveSocket.publish('/app/session/7/hand/raise', {})).toBe(true)
    expect(client.publish).toHaveBeenCalledWith({
      destination: '/app/session/7/hand/raise',
      body: '{}',
    })
    liveSocket.disconnect()
    expect(client.deactivate).toHaveBeenCalledOnce()
  })
})
