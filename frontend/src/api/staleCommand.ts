import { MatchApiError } from './client'

interface VersionedView {
  version: number
}

interface StaleCommandOptions<TView extends VersionedView, TResult> {
  expectedVersion: number
  commandId: string
  execute: (expectedVersion: number, commandId: string) => Promise<TResult>
  refetch: () => Promise<TView>
  canRetry: (refreshedView: TView) => boolean
}

const isStaleVersion = (error: unknown): error is MatchApiError =>
  error instanceof MatchApiError && error.code === 'STALE_VERSION'

export async function executeWithOneStaleRetry<TView extends VersionedView, TResult>({
  expectedVersion,
  commandId,
  execute,
  refetch,
  canRetry,
}: StaleCommandOptions<TView, TResult>): Promise<TResult> {
  try {
    return await execute(expectedVersion, commandId)
  } catch (firstError) {
    if (!isStaleVersion(firstError)) throw firstError

    const refreshedView = await refetch()
    if (!canRetry(refreshedView)) {
      throw new MatchApiError('STALE_RECOVERY_UNSAFE', 409, {
        submittedVersion: expectedVersion,
        refreshedVersion: refreshedView.version,
      })
    }

    try {
      return await execute(refreshedView.version, commandId)
    } catch (secondError) {
      if (!isStaleVersion(secondError)) throw secondError
      throw new MatchApiError('STALE_RETRY_CONFLICT', 409, {
        submittedVersion: expectedVersion,
        refreshedVersion: refreshedView.version,
      })
    }
  }
}
