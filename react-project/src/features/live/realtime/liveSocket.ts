import { Client, type IMessage } from '@stomp/stompjs'
import type {
  HandQueue,
  InteractionPush,
  InteractionTiming,
  LiveRole,
  LiveSocketEvent,
  LiveStats,
  OnlineStudents,
  QAMessage,
  ReactionMessage,
  TeacherStatus,
} from '@/features/live/model/types'

export type LiveSocketOptions = {
  role: LiveRole
  sessionId: number
  token?: string
  onStatus: (status: 'disconnected' | 'connecting' | 'connected') => void
  onEvent: (event: LiveSocketEvent) => void
}

export function liveSocketUrl(location: Pick<Location, 'protocol' | 'host'> = window.location) {
  const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${location.host}/ws/live`
}

function parse<T>(message: IMessage): T {
  return JSON.parse(message.body) as T
}

class LiveSocket {
  private client: Client | null = null

  connect(options: LiveSocketOptions) {
    this.disconnect()
    options.onStatus('connecting')
    const headers: Record<string, string> = { 'X-Session-Id': String(options.sessionId) }
    if (options.token) headers.Authorization = `Bearer ${options.token}`

    const client = new Client({
      brokerURL: liveSocketUrl(),
      connectHeaders: headers,
      reconnectDelay: 5_000,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      debug: () => undefined,
      onConnect: () => {
        if (this.client !== client) return
        options.onStatus('connected')
        this.subscribe(client, options)
      },
      onStompError: () => this.markDisconnected(client, options),
      onWebSocketClose: () => this.markDisconnected(client, options),
      onWebSocketError: () => this.markDisconnected(client, options),
    })
    this.client = client
    client.activate()
  }

  publish(destination: string, body: object) {
    if (!this.client?.connected) return false
    this.client.publish({ destination, body: JSON.stringify(body) })
    return true
  }

  disconnect() {
    const client = this.client
    this.client = null
    if (client) void client.deactivate()
  }

  private markDisconnected(client: Client, options: LiveSocketOptions) {
    if (this.client === client) options.onStatus('disconnected')
  }

  private subscribe(client: Client, options: LiveSocketOptions) {
    const prefix = `/topic/session/${options.sessionId}`
    const subscribe = <T>(suffix: string, type: LiveSocketEvent['type']) =>
      client.subscribe(`${prefix}${suffix}`, (message) =>
        options.onEvent({ type, payload: parse<T>(message) } as LiveSocketEvent),
      )

    subscribe<InteractionPush>('/interaction', 'interaction')
    subscribe<InteractionTiming>('/interaction-timing', 'timing')
    subscribe<HandQueue>('/hand-queue', 'handQueue')

    if (options.role === 'teacher') {
      subscribe<LiveStats>('/stats', 'stats')
      subscribe<QAMessage>('/qa', 'qa')
      subscribe<OnlineStudents>('/students', 'presence')
      subscribe<ReactionMessage>('/reactions', 'reaction')
    } else {
      subscribe<TeacherStatus>('/teacher-status', 'teacherStatus')
    }
  }
}

export const liveSocket = new LiveSocket()
