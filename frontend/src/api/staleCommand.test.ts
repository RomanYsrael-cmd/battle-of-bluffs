import { describe, expect, it, vi } from 'vitest'
import { MatchApiError } from './client'
import { executeWithOneStaleRetry } from './staleCommand'

describe('bounded stale command recovery', () => {
  it('reuses the command identifier for the single safe stale retry', async () => {
    const execute = vi.fn()
      .mockRejectedValueOnce(new MatchApiError('STALE_VERSION', 409))
      .mockResolvedValueOnce('accepted')
    const refetch = vi.fn().mockResolvedValue({ version: 8 })

    await expect(executeWithOneStaleRetry({
      expectedVersion: 7,
      commandId: 'same-command-id',
      execute,
      refetch,
      canRetry: () => true,
    })).resolves.toBe('accepted')

    expect(execute.mock.calls).toEqual([
      [7, 'same-command-id'],
      [8, 'same-command-id'],
    ])
  })

  it('does not retry an unknown network outcome', async () => {
    const execute = vi.fn().mockRejectedValue(new Error('connection reset'))
    const refetch = vi.fn()

    await expect(executeWithOneStaleRetry({
      expectedVersion: 7,
      commandId: 'uncertain-command-id',
      execute,
      refetch,
      canRetry: () => true,
    })).rejects.toThrow('connection reset')

    expect(execute).toHaveBeenCalledTimes(1)
    expect(refetch).not.toHaveBeenCalled()
  })
})
