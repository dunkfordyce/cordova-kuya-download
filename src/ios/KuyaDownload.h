#import <Cordova/CDVPlugin.h>
#import <Cordova/CDVPluginResult.h>
#import <Foundation/Foundation.h>
#import "ASIHTTPRequestDelegate.h"

@interface KuyaDownload : CDVPlugin
{
    NSMutableSet* delegates;
}

@property (retain) NSMutableSet* delegates;

-(void) emitEvent:(NSString*)evname object:(NSObject*) result;
-(void) commandReply:(CDVInvokedUrlCommand*) command withDictionary:(NSDictionary*) obj;
-(void) commandReply:(CDVInvokedUrlCommand*) command withError:(NSString*) msg;
-(void) commandReply:(CDVInvokedUrlCommand*) command withBool:(Boolean) v;

-(void) init:(CDVInvokedUrlCommand*) command;

-(void) download:(CDVInvokedUrlCommand*)command;


-(void) dealloc;
-(void) pluginInitialize;
@end

@interface KuyaDownloadRequestDelegate : NSObject <ASIHTTPRequestDelegate>
{
    KuyaDownload* plugin;
    CDVInvokedUrlCommand* command;
}
@property (retain) KuyaDownload* plugin;
@property (retain) CDVInvokedUrlCommand* command;

- (void)requestFinished:(ASIHTTPRequest *)request;
- (void)requestFailed:(ASIHTTPRequest *)request;

@end
 

