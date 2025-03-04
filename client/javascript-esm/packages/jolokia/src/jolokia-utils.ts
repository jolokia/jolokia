import {
  JolokiaErrorResponse,
  JolokiaSuccessResponse,
  VersionResponseValue
} from "./jolokia-types.js"

export type ParseResult<T> = { hasError: false; parsed: T } | { hasError: true; error: string }

function isObject(value: unknown): value is object {
  const type = typeof value
  return value != null && (type === 'object' || type === 'function')
}

export function isResponseSuccessType(o: unknown): o is JolokiaSuccessResponse {
  return isObject(o) && 'status' in o && 'timestamp' in o && 'value' in o
}

export function isResponseErrorType(o: unknown): o is JolokiaErrorResponse {
  return isObject(o) && 'error_type' in o && 'error' in o
}

export function isVersionResponseType(o: unknown): o is VersionResponseValue {
  return isObject(o) && 'protocol' in o && 'agent' in o
}

/**
 * Parses a jolokia response body and determine its type.
 *
 * If the body is an error then the ParseResult
 * - hasError flag is set to true
 * - error property is populated
 *
 * If the body is successfully parsed then the ParseResult
 * - hasError flag is set to false
 * - parsed property is populated
 */
export async function responseParse(response: unknown): Promise<ParseResult<JolokiaSuccessResponse | JolokiaErrorResponse>> {
  try {
    if (isResponseErrorType(response)) {
      const errorResponse: JolokiaErrorResponse = response as JolokiaErrorResponse
      return { error: errorResponse.error, hasError: true }
    } else if (isResponseSuccessType(response)) {
      const parsedResponse: JolokiaSuccessResponse = response as JolokiaSuccessResponse
      return { parsed: parsedResponse, hasError: false }
    } else {
      return { error: 'Unrecognised jolokia response', hasError: true }
    }
  } catch (e) {
    let msg
    if (e instanceof Error) msg = e.message
    else msg = String(e)

    return { error: msg, hasError: true }
  }
}
