import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { afterEach, beforeEach } from 'vitest'
import { primeCsrfTokenForTest } from './api/http'

beforeEach(() => primeCsrfTokenForTest('X-CSRF-TOKEN', 'test-csrf-token'))
afterEach(cleanup)
