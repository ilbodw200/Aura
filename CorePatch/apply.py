from pathlib import Path
import sys,shutil
root=Path(__file__).resolve().parent
core=Path(sys.argv[1]).resolve()
def replace(file,old,new):
 path=core/file;text=path.read_text()
 if new in text:return
 assert text.count(old)==1,file+' upstream changed'
 path.write_text(text.replace(old,new))
shutil.copyfile(root/'aura_probe.go',core/'experimental/libbox/aura_probe.go')
shutil.copyfile(root/'aura_fragment.go',core/'common/dialer/aura_fragment.go')
shutil.copyfile(root/'aura_fragment_test.go',core/'common/dialer/aura_fragment_test.go')
replace('option/outbound.go','type DialerOptions struct {','''type AuraTLSFragmentOptions struct {
 LengthMin int `json:"length_min"`
 LengthMax int `json:"length_max"`
 DelayMin int `json:"delay_min"`
 DelayMax int `json:"delay_max"`
}

type DialerOptions struct {
 AuraTLSFragment *AuraTLSFragmentOptions `json:"aura_tls_fragment,omitempty"`''')
replace('common/dialer/dialer.go','\treturn dialer, nil\n}', '\tif options.AuraTLSFragment != nil { return auraWrapFragment(dialer, options.AuraTLSFragment) }\n\treturn dialer, nil\n}')
replace('option/v2ray_transport.go','type V2RayGRPCOptions struct {','type V2RayGRPCOptions struct {\n Authority string `json:"authority,omitempty"`')
replace('transport/v2raygrpc/client.go','\tvar dialOptions []grpc.DialOption','\tvar dialOptions []grpc.DialOption\n if options.Authority != "" { dialOptions = append(dialOptions, grpc.WithAuthority(options.Authority)) }')
replace('transport/v2raygrpclite/client.go','\tclient := &Client{','\tif options.Authority != "" { host = options.Authority }\n\tclient := &Client{')
print('Applied AURA probes, TLS record fragmentation and gRPC authority to pinned core.')
