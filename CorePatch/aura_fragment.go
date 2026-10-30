package dialer

import (
 "context"
 "crypto/rand"
 "encoding/binary"
 "errors"
 "io"
 "net"
 "sync"
 "time"

 "github.com/sagernet/sing-box/option"
 M "github.com/sagernet/sing/common/metadata"
 N "github.com/sagernet/sing/common/network"
)

type auraFragmentDialer struct { N.Dialer; options option.AuraTLSFragmentOptions }

func auraWrapFragment(d N.Dialer, options *option.AuraTLSFragmentOptions) (N.Dialer,error) {
 if options.LengthMin<1 || options.LengthMax<options.LengthMin || options.LengthMax>16384 || options.DelayMin<0 || options.DelayMax<options.DelayMin || options.DelayMax>100 { return nil,errors.New("invalid AURA TLS fragment bounds") }
 return &auraFragmentDialer{d,*options},nil
}
func (d *auraFragmentDialer) DialContext(ctx context.Context, network string, destination M.Socksaddr)(net.Conn,error) {
 conn,err:=d.Dialer.DialContext(ctx,network,destination)
 if err!=nil{return nil,err};if network!="tcp"{return conn,nil}
 return &auraFragmentConn{Conn:conn,options:d.options,ctx:ctx},nil
}
type auraFragmentConn struct {
 net.Conn
 options option.AuraTLSFragmentOptions
 ctx context.Context
 lock sync.Mutex
 written bool
}
func auraRandom(low,high int) int {
 if low==high{return low};var b [4]byte
 if _,err:=rand.Read(b[:]);err!=nil{return low}
 return low+int(binary.BigEndian.Uint32(b[:])%uint32(high-low+1))
}
func auraWriteAll(conn net.Conn, data []byte) error {
 for len(data)>0 { n,err:=conn.Write(data);if err!=nil{return err};if n==0{return io.ErrShortWrite};data=data[n:] };return nil
}
func (c *auraFragmentConn) Write(data []byte)(int,error) {
 c.lock.Lock();defer c.lock.Unlock()
 if c.written{return c.Conn.Write(data)};c.written=true
 // Split only the initial complete TLS handshake record; preserve the stream otherwise.
 if len(data)<6 || data[0]!=22 || data[5]!=1{return c.Conn.Write(data)}
 end:=5+int(binary.BigEndian.Uint16(data[3:5]))
 if end>len(data)||end<=5{return c.Conn.Write(data)}
 records:=make([]byte,0,len(data)+len(data)/c.options.LengthMin*5)
 for offset:=5;offset<end; {
  length:=auraRandom(c.options.LengthMin,c.options.LengthMax);if offset+length>end{length=end-offset}
  record:=make([]byte,5+length);copy(record[:3],data[:3]);binary.BigEndian.PutUint16(record[3:5],uint16(length));copy(record[5:],data[offset:offset+length]);offset+=length
  if c.options.DelayMax==0 {records=append(records,record...)} else {
   if err:=auraWriteAll(c.Conn,record);err!=nil{return 0,err}
   timer:=time.NewTimer(time.Duration(auraRandom(c.options.DelayMin,c.options.DelayMax))*time.Millisecond)
   select {case <-c.ctx.Done():timer.Stop();return 0,c.ctx.Err();case <-timer.C:}
  }
 }
 if len(records)>0 {if err:=auraWriteAll(c.Conn,records);err!=nil{return 0,err}}
 if err:=auraWriteAll(c.Conn,data[end:]);err!=nil{return end,err}
 return len(data),nil
}
func (c *auraFragmentConn) CloseWrite()error {
 if conn,ok:=c.Conn.(interface{CloseWrite()error});ok{return conn.CloseWrite()};return c.Conn.Close()
}
