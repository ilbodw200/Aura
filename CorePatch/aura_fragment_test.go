package dialer

import (
 "bytes"
 "context"
 "crypto/tls"
 "crypto/x509"
 "encoding/binary"
 "io"
 "net"
 "net/http"
 "net/http/httptest"
 "testing"
 "time"

 "github.com/sagernet/sing-box/option"
)

type auraCaptureConn struct { net.Conn; bytes.Buffer }
func (c *auraCaptureConn) Read(p []byte)(int,error){return 0,io.EOF}
func (c *auraCaptureConn) Write(p []byte)(int,error){return c.Buffer.Write(p)}

func TestAuraFragmentPreservesHandshakeAndTrailingRecords(t *testing.T){
 capture:=&auraCaptureConn{}
 conn:=&auraFragmentConn{Conn:capture,ctx:context.Background(),options:option.AuraTLSFragmentOptions{LengthMin:10,LengthMax:20}}
 payload:=append([]byte{1},bytes.Repeat([]byte{7},399)...)
 first:=append([]byte{22,3,1,byte(len(payload)>>8),byte(len(payload))},payload...)
 trailer:=[]byte{20,3,3,0,1,1};data:=append(first,trailer...)
 n,err:=conn.Write(data);if err!=nil||n!=len(data){t.Fatal(n,err)}
 wire:=capture.Bytes();restored:=[]byte{};count:=0
 for len(restored)<len(payload){
  if len(wire)<5 || wire[0]!=22 {t.Fatal("invalid TLS record")}
  length:=int(binary.BigEndian.Uint16(wire[3:5]));if length>20||length<1||len(wire)<5+length{t.Fatal("fragment bounds")}
  restored=append(restored,wire[5:5+length]...);wire=wire[5+length:];count++
 }
 if count<20||!bytes.Equal(restored,payload)||!bytes.Equal(wire,trailer){t.Fatal("stream changed")}
 capture.Reset();next:=[]byte("application data");conn.Write(next)
 if !bytes.Equal(capture.Bytes(),next){t.Fatal("fragmented a later write")}
}
func TestAuraFragmentRealTLSHandshake(t *testing.T){
 server:=httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter,r *http.Request){w.WriteHeader(204)}));defer server.Close()
 roots:=x509.NewCertPool();roots.AddCert(server.Certificate())
 raw,err:=net.DialTimeout("tcp",server.Listener.Addr().String(),time.Second);if err!=nil{t.Fatal(err)};defer raw.Close();raw.SetDeadline(time.Now().Add(5*time.Second))
 wrapped:=&auraFragmentConn{Conn:raw,ctx:context.Background(),options:option.AuraTLSFragmentOptions{LengthMin:10,LengthMax:20}}
 secure:=tls.Client(wrapped,&tls.Config{RootCAs:roots,ServerName:"127.0.0.1"})
 if err=secure.Handshake();err!=nil{t.Fatal(err)}
 if _,err=io.WriteString(secure,"GET / HTTP/1.1\r\nHost: example.com\r\nConnection: close\r\n\r\n");err!=nil{t.Fatal(err)}
 reply:=make([]byte,128);n,err:=secure.Read(reply);if err!=nil{t.Fatal(err)};if !bytes.Contains(reply[:n],[]byte("204")){t.Fatal("missing HTTP response")}
}
func TestAuraFragmentRejectsInvalidBounds(t *testing.T){
 for _,settings:=range []option.AuraTLSFragmentOptions{{LengthMin:0,LengthMax:20},{LengthMin:30,LengthMax:20},{LengthMin:1,LengthMax:20,DelayMax:101}}{
  if _,err:=auraWrapFragment(nil,&settings);err==nil{t.Fatal("invalid options accepted")}
 }
}
