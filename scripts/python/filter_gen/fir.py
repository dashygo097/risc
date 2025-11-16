import sys

from scipy.signal import firwin

filter_type = sys.argv[1]
fs = float(sys.argv[2])
num_taps = int(sys.argv[3])

if filter_type == "bandpass":
    f1 = float(sys.argv[4])
    f2 = float(sys.argv[5])
    cutoff = [f1, f2]
else:
    cutoff = float(sys.argv[4])

taps = firwin(
    num_taps, cutoff, fs=fs, pass_zero=filter_type == "lowpass", window="hamming"
)

print(",".join(map(str, taps.tolist())))
