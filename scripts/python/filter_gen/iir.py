import sys

from scipy.signal import iirfilter

filter_type = sys.argv[1]  # 'lowpass', 'highpass', 'bandpass', or 'bandstop'
fs = float(sys.argv[2])  # Sampling frequency
order = int(sys.argv[3])  # Filter order

if filter_type in ("bandpass", "bandstop"):
    f1 = float(sys.argv[4])
    f2 = float(sys.argv[5])
    cutoff = [f1, f2]
else:
    cutoff = float(sys.argv[4])

# Design IIR filter
b, a = iirfilter(  # pyright: ignore
    order,
    cutoff,
    btype=filter_type,
    ftype="butter",
    fs=fs,
)

# Print numerator and denominator coefficients separately
print("b=" + ",".join(map(str, b.tolist())))
print("a=" + ",".join(map(str, a.tolist())))
