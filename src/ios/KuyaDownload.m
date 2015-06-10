#import "KuyaDownload.h"
#import "ASIHTTPRequest.h"

#define NILABLE(obj) ((obj) != nil ? (NSObject *)(obj) : (NSObject *)[NSNull null])

@implementation KuyaDownloadRequestDelegate

@synthesize plugin = _plugin;
@synthesize command = _command;

- (void)requestFinished:(ASIHTTPRequest *)request
{
    //[self.plugin commandReply:self.command withBool:true];
    [self.plugin emitEvent:@"request finished" object: [[NSDictionary alloc] initWithObjectsAndKeys:
                                                        [request responseHeaders], @"headers",
                                                        [request.url absoluteString], @"url",
                                                        nil]
    ];
    
    [self.plugin.delegates removeObject:self];
}

- (void)requestFailed:(ASIHTTPRequest *)request
{
    NSError* err = [request error];
    [self.plugin emitEvent:@"request failed" object:[err localizedDescription]];
    //[self.plugin commandReply:command withError: [err localizedDescription]];
    [self.plugin.delegates removeObject:self];
}

@end

@implementation KuyaDownload

@synthesize delegates = _delegates;

static KuyaDownload * _scManager;

-(void) pluginInitialize
{
    NSLog(@"pluginInitialize()");
    //[self emitEvent:@"ready" object:nil];
}

-(void) init:(CDVInvokedUrlCommand*) command
{
    NSLog(@"init()");
    self.delegates = [[NSMutableSet alloc]init];
}


-(void)download:(CDVInvokedUrlCommand*)command
{
    NSString* url = [command.arguments objectAtIndex:0];
    NSString* dest = [command.arguments objectAtIndex:1];
    NSMutableDictionary* headers = [command.arguments objectAtIndex:2];
    KuyaDownloadRequestDelegate *delegate = [[KuyaDownloadRequestDelegate alloc] init];
    
    NSLog(@"downloading %@ %@", url, dest);
    
    delegate.plugin = self;
    delegate.command = command;
    
    [self.delegates addObject: delegate];
    
    ASIHTTPRequest *request = [ASIHTTPRequest requestWithURL:[NSURL URLWithString:url]];
    [request setRequestHeaders:headers];
    [request setDownloadDestinationPath:dest];
    [request setDelegate:delegate];
    [request startAsynchronous];
    
    [self commandReply:command withBool:true];
}


-(void) export:(NSString*)name object:(NSDictionary*) obj
{
    NSError *error = nil;
    NSData* json = [NSJSONSerialization dataWithJSONObject:obj options: 0 error:&error];
    if( error ) {
        NSLog(@"failed creating json %@", error.debugDescription);
    }
    NSString *jsonString = [[NSString alloc] initWithData:json encoding:NSUTF8StringEncoding];
    
    NSString *js = [NSString
                    stringWithFormat:@"window.kuyashop.%@=%@;", name, jsonString
                    ];
    [self.commandDelegate evalJs:js];
}

-(void) emitEvent:(NSString*)evname object:(NSObject*)result
{
    NSError *error = nil;
    NSDictionary *wrapper = [ [NSDictionary alloc] initWithObjectsAndKeys :
                             result != nil ? (NSObject*)result : (NSObject *)[NSNull null] , @"data",
                             evname, @"type",
                             @"kuyadownload", @"plugin",
                             nil
                             ];
    
    NSData* json = [NSJSONSerialization dataWithJSONObject:wrapper options: 0 error:&error];
    
    if( error ) {
        NSLog(@"failed creating json %@", error.debugDescription);
    }
    
    NSString *jsonString = [[NSString alloc] initWithData:json encoding:NSUTF8StringEncoding];
    
    NSString *js = [NSString
                    stringWithFormat:@"window.cordova_plugin._emit(%@)",
                    jsonString];
    
    
    
    NSLog(@"emitting %@", js);
    [self.commandDelegate evalJs:js];
}

-(void) commandReply:(CDVInvokedUrlCommand*) command withDictionary:(NSDictionary*) obj
{
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                                        messageAsDictionary: obj
                                     ];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

-(void) commandReply:(CDVInvokedUrlCommand*) command withBool:(Boolean) v
{
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                                                  messageAsBool:v
                                     ];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

-(void) commandReply:(CDVInvokedUrlCommand*) command withError:(NSString*) msg
{
    NSDictionary* err = [[NSDictionary alloc] initWithObjectsAndKeys:
                         msg, @"message",
                         nil];
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR
                                                  messageAsDictionary: err
                                     ];
    [self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}
    

- (void)dealloc
{

}

@end
