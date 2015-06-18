#import "KuyaDownload.h"
#import "ASIHTTPRequest.h"

#define NILABLE(obj) ((obj) != nil ? (NSObject *)(obj) : (NSObject *)[NSNull null])

@implementation KuyaDownloadRequestDelegate

@synthesize plugin = _plugin;
@synthesize command = _command;
@synthesize download_id = _download_id;

- (void)requestFinished:(ASIHTTPRequest *)request
{
    [self.plugin emitEvent:@"finished" object: [[NSDictionary alloc] initWithObjectsAndKeys:
                                                        [NSNumber numberWithInt:self.download_id], @"id",
                                                        [request responseHeaders], @"headers",
                                                        [request.url absoluteString], @"url",
                                                        [NSNumber numberWithInt:[request responseStatusCode]], @"status",
                                                        nil]
    ];
    
    [self.plugin.delegates removeObject:self];
    request.delegate = nil;
}

- (void)requestFailed:(ASIHTTPRequest *)request
{
    NSError* err = [request error];
    [self.plugin emitEvent:@"failed" object:  [[NSDictionary alloc] initWithObjectsAndKeys:
                                                        [NSNumber numberWithInt:self.download_id], @"id",
                                                        [err localizedDescription], "@message",
                                                        nil]
    ];
    [self.plugin.delegates removeObject:self];
    request.delegate = nil;
}

- (void)request:(ASIHTTPRequest *)request didReceiveBytes:(long long)bytes
{
    NSLog(@"progress %@ %llu %llull", request.url.absoluteString, request.contentLength, request.totalBytesRead);
    [self.plugin emitEvent:@"progress" object:[[NSDictionary alloc] initWithObjectsAndKeys:
                                               [NSNumber numberWithInt:self.download_id], @"id",
                                               [NSNumber numberWithLongLong: request.contentLength], @"total",
                                               [NSNumber numberWithLongLong: request.totalBytesRead], @"downloaded",
                                               nil]
     ];
}

@end

@implementation KuyaDownload

@synthesize delegates = _delegates;
@synthesize download_id = _download_id;

static KuyaDownload * _scManager;


-(void) pluginInitialize
{
    NSLog(@"pluginInitialize()");
    self.delegates = [[NSMutableSet alloc]init];
    self.download_id = 0;
}

-(void) init:(CDVInvokedUrlCommand*) command
{
    NSLog(@"init()");
}


-(void)download:(CDVInvokedUrlCommand*)command
{
    NSString* url = [command.arguments objectAtIndex:0];
    NSString* dest = [command.arguments objectAtIndex:1];
    NSMutableDictionary* headers = [command.arguments objectAtIndex:2];
    KuyaDownloadRequestDelegate *delegate = [[KuyaDownloadRequestDelegate alloc] init];
    
    NSLog(@"downloading %@ %@ %@", url, dest, headers);
    
    delegate.plugin = self;
    delegate.command = command;
    delegate.download_id = self.download_id++;
    
    [self.delegates addObject: delegate];
    
    ASIHTTPRequest *request = [ASIHTTPRequest requestWithURL:[NSURL URLWithString:url]];
    [request setRequestHeaders:headers];
    request.shouldRedirect = false;
    [request setDownloadProgressDelegate:delegate];
    [request setDownloadDestinationPath:dest];
    [request setDelegate:delegate];
    [request startAsynchronous];
    
    [self commandReply:command withInteger:delegate.download_id];
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

-(void) commandReply:(CDVInvokedUrlCommand*) command withInteger:(NSInteger) v
{
    CDVPluginResult* pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK
                                                         messageAsInt:v
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
    self.delegates = nil;
}

@end
