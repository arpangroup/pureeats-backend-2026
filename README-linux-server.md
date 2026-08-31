# Linux Utility Commands



## 1. Check Disk Space

## 1.1. check the total space and remaining space
```bash
df -h
```

Output:
```
Filesystem      Size  Used Avail Use% Mounted on
tmpfs            97M  1.1M   96M   2% /run
/dev/vda1        24G  5.0G   19G  22% /
tmpfs           481M  1.1M  480M   1% /dev/shm
tmpfs           5.0M     0  5.0M   0% /run/lock
/dev/vda16      881M   64M  756M   8% /boot
/dev/vda15      105M  6.2M   99M   6% /boot/efi
tmpfs            97M   12K   97M   1% /run/user/0
```

## 1.2. Check exactly how much space is left on the specific partition holding your log folder
```bash
df -h /var/log/
df -h /var/log/pureeats/pureeats-api.log
```

Output:
```
Filesystem      Size  Used Avail Use% Mounted on
/dev/vda1        24G  5.0G   19G  22% /
```



