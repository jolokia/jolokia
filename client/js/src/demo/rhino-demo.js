/**
   Demo of writing a custom JolokiaJS.Request.prototype.postBackend().
   This one uses the Rhino JavaScript engine, which allows us to use
   Java functionality for the network support.

   Mis-features:

   - Only POST is supported by this implementation.

   - It does not support asynchronous operation. It shouldn't be too
   hard to add via a new Java thread, though handling timeouts
   accurately might be complicated by blocking read operations unless
   maybe there's another thread watching the i/o thread. Even then,
   we might not have a way of cancelling a blocking i/o operation.

   - It is a quick hack and only lightly tested.
*/

// Import JolokiaJS-required scripts. Adjust the paths as needed!
(function() {
    var srcdir = './..';
    var includes = [srcdir+'/json2.js',
                    srcdir+'/JolokiaJS.js'
                    ];
    for( var i in includes ) {
        load(includes[i]);
    }
})();


// The concrete postBackend() implementation we will use:
JolokiaJS.Request.postImpl.concrete.rhinoJava = function(args) {
    var request = this;
    try {
        var url = new java.net.URL( args.url );
        var con = url.openConnection();
        con.setDoOutput( true );
        var IO = new JavaImporter(java.io);
        var wr = new IO.OutputStreamWriter(con.getOutputStream());
        var req = this;
        (function() {
            wr.write(req.toJ4PString());
            wr.flush();
        })();
        var rd = new IO.BufferedReader(new IO.InputStreamReader(con.getInputStream()));
        var line;
        var json = []
        while ((line = rd.readLine()) != null) {
            json.push(line);
        }
        try { wr.close(); } catch(e) { /*ignore*/}
        try { rd.close(); } catch(e) { /*ignore*/}
        JolokiaJS.Request.postImpl.onPostSuccess( this, json.join(''), args );
    }
    catch(e) {
        args.errorMessage = e.toString();
        JolokiaJS.Request.postImpl.onPostError( request, args );
        return undefined;
    }
};

/************************************************************************
Configure JolokiaJS.Request to use our implementation...
 ************************************************************************/
JolokiaJS.Request.options.ajax.url =
    // local dev server:
    'http://localhost:8080/jolokia/'
    ;

// Default onError() handler:
JolokiaJS.Request.options.ajax.onError = function(req,opt) {
    print("REQUEST ERROR:\n"+opt.errorMessage);
    print("Connection parameters were:\n",JSON.stringify(opt,null,4));
};

// Default beforePost() handler:
JolokiaJS.Request.options.ajax.beforePost = function(req,opt) {
    print("POSTing '"+req.type()+"' request to",opt.url,':\n'+req.toJ4PString(4));
};
JolokiaJS.Request.options.ajax.afterPost = function(req,opt) {
    print("POSTing finished.");
};

// Default onSuccess() handler:
JolokiaJS.Request.options.ajax.onSuccess = function(resp,req) {
    print("GOT RESPONSE:\n",resp.toJ4PString(4));
};

// Install the rhino-based back-end as the default post() implementation:
JolokiaJS.Request.prototype.postBackend = JolokiaJS.Request.postImpl.concrete.rhinoJava;

// Now let's see if it works...
function demoRhinoRequests() {
    (new JolokiaJS.Request({
            type:'READ',
            mbean:'java.lang:type=Memory',
            attribute:"HeapMemoryUsage"
    })).post();
}

demoRhinoRequests();
