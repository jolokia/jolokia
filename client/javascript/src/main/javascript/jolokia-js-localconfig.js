/* site-dependent JolokiaJS configuration.

    This file gets imported by JolokiaJS.js.php. Clients not
    using that file should import it themselves, if needed,
    to customize any JolokiaJS options for their application.
*/

/** If you install the JolokiaJS code somewhere other than the
    layout used by its primary source distribution, you must update
    JolokiaJS.Request.options.ajax.url to match, e.g.:
*/
//JolokiaJS.Request.options.ajax.url = 'include/JSONRequest-dispatch.php';


//JolokiaJS.Request.options.ajax.asynchronous = false;
//JolokiaJS.Request.options.ajax.method = 'GET';
//JolokiaJS.Request.options.ajax.timeout = 12000;

/** JolokiaJS.options.toJSONSpacing specifies the amount of spacing to
    use when JolokiaJS.toJSON() is called. Set it to 0/null/undefined/false
    to disable this prettification. It can be any value supported by the 3rd
    argument to JSON.stringify().
*/
//JolokiaJS.options.toJSONSpacing = 2;

// The default ajax back-end implementation:
//JolokiaJS.Request.prototype.postBackend = JolokiaJS.Request.postImpl.concrete.XMLHttpRequest;

// Using dojo for ajax:
//JolokiaJS.Request.prototype.postBackend = JolokiaJS.Request.postImpl.concrete.dojo;

// Using jQuery for ajax:
//JolokiaJS.Request.prototype.postBackend = JolokiaJS.Request.postImpl.concrete.jQuery;

// Using Prototype for ajax: (Requires 1.7+, as <=1.6.x have broken JSON support.)
//JolokiaJS.Request.prototype.postBackend = JolokiaJS.Request.postImpl.concrete.Prototype;



/** On 2010 April 26, the classes JSONRequest and JSONResponse were renamed
    to JolokiaJS.Request resp. JolokiaJS.Response, to (A) cut down on global
    namespace polution and (B) avoid any conflict/confusion with Doug Crockford's
    JSONRequest API. If you prefer to save a bit of typing and don't mind a naming
    collision with Crockford's API:
*/
//JSONRequest = JolokiaJS.Request;
//JSONResponse = JolokiaJS.Response;
