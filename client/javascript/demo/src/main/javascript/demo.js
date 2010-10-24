
JolokiaJS.Request.prototype.postBackend =
    //JolokiaJS.Request.postImpl.concrete.XMLHttpRequest
    JolokiaJS.Request.postImpl.concrete.jQuery
;

function loadFirebugLite() {
    var fb=document.createElement('script');
    fb.setAttribute('src',
                    'https://getfirebug.com/firebug-lite.js'
                    //'firebug-lite.js'
                    );
    document.body.appendChild(fb);
};

function sendViaRequest() {

    var taRes = jQuery('#taRESPONSE');
    taRes.attr('value',"Sending...");

    // Get JSON request from TEXTAREA:
    var json = jQuery('#taREQUEST').attr('value');
    try {
        json = JSON.parse(json);
    }
    catch(e) {
        alert("Could not parse request as JSON:\n"+json);
        return;
    }
    var req = new JolokiaJS.Request(json);
    var popt = {
        method:'POST',
        onSuccess:function(response,request) {
            //alert("REQUEST.toJ4PString():\n"+request.toJ4PString());
            var val = response.toJ4PString(4);
            taRes.attr('value',val);
        },
        onError:function(request,requestOpt) {
            taRes.attr('value',"ERROR:\n"+JSON.stringify(requestOpt,undefined,4));
        },
        beforePost: function(request, connectOpt)
        {
            //alert("Posting:\n"+request.toJSON()+"Options:\n"+JSON.stringify(connectOpt,undefined,4));
            return true;
        },
        afterPost: function(request, connectOpt)
        {
        }
    };
    req.post(popt);
}

////////////////////////////////////////////////////////////////////////
// updatePlot() code by http://banjo.rbfh.de:11111/graphs/
// Updates a visual graphic of server memory usage.
var used        = [];
var max         = [];
var comitted    = [];
var init        = [];
var start       = (new Date()).getTime() / 1000;
function updatePlot(resp,req) {
//  value     => { committed => 15925248, init => 16397184, max => 508887040, used => 5861056 },
    var val = resp.value();
    val.max       /= 1024*1024;
    val.init      /= 1024*1024;
    val.used      /= 1024*1024;
    val.committed  /= 1024*1024;

    $("#max").html(val.max.toFixed(3));
    $("#init").html(val.init.toFixed(3));
    $("#committed").html(val.committed.toFixed(3));
    $("#used").html(val.used.toFixed(3));

    var maxval = 150;
    if(used.length > maxval) used.shift();
    if(max.length > maxval) max.shift();
    if(comitted.length > maxval) comitted.shift();
    if(init.length > maxval) init.shift();

    var ts = parseInt( resp.timestamp() );
    start = ts;
    ts *= 1000.0;
    
    used.push([ts, val.used]);
    max.push([ts, val.max]);
    init.push([ts, val.init]);
    comitted.push([ts, val.comitted]);

    jQuery('#memoryResponse').text( resp.toJ4PString(4) );
    $.plot($('#heap'), [
        { data: used, label: "Used" },
        { data: max,  label: "Max" },
        { data: init, label: "Init" },
        { data: comitted, label: "Comitted" }
    ], {
        xaxis: {
            mode: "time",
            tickFormatter: function(val, axis) {
                var d = (new Date(val)).getTime() / 1000;
                var diff = start - d;
                return -diff.toFixed(1) + "";
            },
            ticks: 7
        },
        legend: { container: $("#legend"), noColumns: 4 }
    });
}

updatePlot.req = (new JolokiaJS.Request({
        type:'READ',
        mbean:'java.lang:type=Memory',
        attribute:'HeapMemoryUsage'
    }))
    .postOptions({
       //url:'errorTest',
        onSuccess: updatePlot,
        onError:function(req,opt) {
            var tid = req.timerID;
            req.timerID = undefined;
            clearInterval( tid );
	    var msg = "Error collecting memory stats! Disabling stat collection loop.\n"
                      + JSON.stringify(opt,undefined,4);
	    jQuery('#memoryResponse').text(msg);
	    jQuery('#heap').html('<pre>'+msg+'</pre>');
          ;
        }
    });

// Switches JolokiaJS.Request.post() impl. b must be-a implementation function.
function switchBackend( b )
{
    JolokiaJS.Request.prototype.postBackend = b;
    jQuery('#taPostImpl').attr('value',b.toString());
}

// obj must be-a simple object form of a Jolokia request. It is turned into a JolokiaJS.Request
// and its JSON'd form is copied to #taREQUEST.
function setRequest(obj) {
    var req;
    try {
        req = new JolokiaJS.Request(obj);
    }
    catch(e) {
        alert("Invalid Request JSON:\n"+JSON.stringify(obj,undefined,4));
        return;
    }
    var taRes = jQuery('#taREQUEST');
    taRes.attr('value',req.toJ4PString(JolokiaJS.options.toJSONSpacing));


}


function setupDemo() {
    switchBackend(
        JolokiaJS.Request.postImpl.concrete.XMLHttpRequest
        //JolokiaJS.Request.postImpl.concrete.jQuery
    );

    // Set up list of pre-selected bean ops...
    var list = [
        {n:"Jolokia version",
         r:{type:'VERSION'}
        },
        {n:"Heap memory (used)",
         r:{type:'READ',mbean:'java.lang:type=Memory',attribute:'HeapMemoryUsage',path:'used'}
        },
        {n:"Heap memory (all)",
         r:{type:'READ',mbean:'java.lang:type=Memory',attribute:'HeapMemoryUsage'}
        },
        {n:"Run GC",
         r:{type:"exec",mbean:'java.lang:type=Memory',operation:'gc'}
        },
        {n:"List java.lang Beans",
         r:{type:'SEARCH',mbean:'java.lang:*'}
        },
        {n:"List Jolokia Beans",
         r:{type:'SEARCH',mbean:'jolokia:*'}
        },
        {n:"List all Beans",
         r:{type:'SEARCH',mbean:'*:*'}
        },
        {n:"Enable Jolokia debug",
         r:{type:"write",mbean:'jolokia:type=Config',attribute:'Debug',value:'true'}
        },
        {n:"Jolokia debug info",
         r:{type:"exec",mbean:'jolokia:type=Config',operation:'debugInfo'}
        },
        {n:"Reset Jolokia debug info",
         r:{type:"exec",mbean:'jolokia:type=Config',operation:'resetDebugInfo'}
        },
        {n:"Intentional error",
         r:{type:'XYZ',mbean:'*:*'}
        }
    ];

    //tgt.html("Select a request:<br/>");
    var buttonSend = jQuery('#buttonSendRequest');
    var cbAutosend = jQuery('#checkboxAutosend');

    // Extra level of indirection needed to capture some local vars. This isn't working
    // for me when i inline this function in the for() loop.
    function captureKludge(a,r) {
        a.click( function() {
            setRequest(r.r);
            if( cbAutosend.attr('checked') ) {
                buttonSend.click();
            }
        });
    }
    var tgt = jQuery('#requestListArea');
    var i, a;
    var x = 0;
    for( i in list ) {
        var r = list[i];
        a = jQuery('<a href="#"></a>').text('['+r.n+']');
        captureKludge(a,r);
        tgt.append(' ').append( a );
        // Go ahead and post the first entry...
        if( 1 == ++x ) {
            a.click();
            if( ! cbAutosend.attr('checked') ) {
                buttonSend.click();
            }
        }
    }
}

function setupMemoryCollector(ms) {
    updatePlot.req.post();
    updatePlot.req.timerID = setInterval( function() { updatePlot.req.post(); }, ms || 5000 );
}

